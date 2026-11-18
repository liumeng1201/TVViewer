(function (site) {
    if (window.tvViewerPlayback) return;

    var observer;
    var observerDeadline;
    var fullscreen = false;
    var playingVideos = new WeakSet();
    var watchedVideos = new Set();

    function select(selector) {
        return selector ? document.querySelector(selector) : null;
    }

    function hasStarted(video) {
        return !video.paused && !video.ended &&
            (playingVideos.has(video) || (video.readyState >= 2 && video.currentTime > 0));
    }

    function onPlaying(event) {
        playingVideos.add(event.currentTarget);
    }

    function watchVideos() {
        Array.from(document.querySelectorAll('video')).forEach(function (video) {
            if (!watchedVideos.has(video)) {
                watchedVideos.add(video);
                video.addEventListener('playing', onPlaying);
            }
        });
        watchedVideos.forEach(function (video) {
            if (!document.contains(video)) {
                video.removeEventListener('playing', onPlaying);
                watchedVideos.delete(video);
            }
        });
    }

    function updatePlayer() {
        watchVideos();
        var button = select(site.fullscreen);
        if (!fullscreen && button) {
            fullscreen = true;
            button.click();
            var video = select('video');
            if (video) video.volume = 1;

            if (site.decorations) {
                Array.from(document.querySelectorAll(site.decorations)).forEach(function (item) {
                    if (!item.querySelector('video')) item.style.display = 'none';
                });
            }
        }
    }

    function stopObserving() {
        observer.disconnect();
        clearTimeout(observerDeadline);
    }

    function dispose() {
        stopObserving();
        watchedVideos.forEach(function (video) {
            video.removeEventListener('playing', onPlaying);
        });
        watchedVideos.clear();
        window.removeEventListener('pagehide', dispose);
        delete window.tvViewerPlayback;
    }

    function programme() {
        var entries = new Set();
        site.programme.forEach(function (selector) {
            Array.from(document.querySelectorAll(selector)).slice(0, 2).forEach(function (item) {
                var text = item.textContent.trim();
                if (text) entries.add(text);
            });
        });
        return Array.from(entries).join('\n');
    }

    window.tvViewerPlayback = {
        status: function () {
            var videos = Array.from(document.querySelectorAll('video'));
            if (videos.some(hasStarted)) return ['playing', programme()];
            if (videos.length && videos.every(function (video) { return video.error; })) {
                return ['error', ''];
            }
            return ['waiting', ''];
        },
        togglePlayback: function () {
            var video = Array.from(document.querySelectorAll('video')).find(hasStarted) || select('video');
            if (!video) return;
            var button = select(video.paused ? site.play : site.pause);
            if (button) {
                button.click();
            } else if (video.paused) {
                var play = video.play();
                if (play) play.catch(function () {});
            } else {
                video.pause();
            }
        },
        toggleFullscreen: function () {
            var button = select(site.exitFullscreen) || select(site.fullscreen);
            if (button) button.click();
        }
    };

    observer = new MutationObserver(updatePlayer);
    observer.observe(document.documentElement, {childList: true, subtree: true, attributes: true,
        attributeFilter: ['class', 'id']});
    observerDeadline = setTimeout(stopObserving, 10_000);
    window.addEventListener('pagehide', dispose);
    updatePlayer();
})
