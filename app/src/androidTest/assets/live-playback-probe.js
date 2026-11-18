function () {
    let video;
    let generation = 0;
    let presentedFrames = 0;
    let callback;

    function visibleArea(element) {
        const rect = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        if (style.visibility === 'hidden' || style.display === 'none' || +style.opacity === 0) {
            return 0;
        }
        return Math.max(0, Math.min(rect.right, innerWidth) - Math.max(rect.left, 0)) *
            Math.max(0, Math.min(rect.bottom, innerHeight) - Math.max(rect.top, 0));
    }

    function track(candidate) {
        if (candidate === video) return;
        if (callback != null && video && video.cancelVideoFrameCallback) {
            video.cancelVideoFrameCallback(callback);
        }
        callback = null;
        video = candidate;
        ++generation;
        presentedFrames = 0;
        if (video && video.requestVideoFrameCallback) {
            const tracked = video;
            const onFrame = (_, metadata) => {
                if (video !== tracked) return;
                presentedFrames = metadata.presentedFrames;
                callback = tracked.requestVideoFrameCallback(onFrame);
            };
            callback = tracked.requestVideoFrameCallback(onFrame);
        }
    }

    function snapshot() {
        const candidates = Array.from(document.querySelectorAll('video'))
            .map(element => ({element, area: visibleArea(element)}))
            .filter(candidate => candidate.area > 0)
            .sort((a, b) => Number(b.element.videoWidth > 0 && !b.element.paused)
                - Number(a.element.videoWidth > 0 && !a.element.paused) || b.area - a.area);
        track(candidates.length ? candidates[0].element : null);

        const status = {
            host: location.hostname,
            state: document.readyState,
            videoFound: !!video
        };
        if (!video) return status;

        const rect = video.getBoundingClientRect();
        const viewport = window.visualViewport;
        const width = viewport ? viewport.width : innerWidth;
        const height = viewport ? viewport.height : innerHeight;
        const x = viewport ? viewport.offsetLeft : 0;
        const y = viewport ? viewport.offsetTop : 0;
        const quality = video.getVideoPlaybackQuality ? video.getVideoPlaybackQuality() : null;
        return Object.assign(status, {
            generation,
            currentTime: video.currentTime,
            paused: video.paused,
            seeking: video.seeking,
            readyState: video.readyState,
            error: video.error ? video.error.code : 0,
            width: video.videoWidth,
            height: video.videoHeight,
            presentedFrames: video.requestVideoFrameCallback ? presentedFrames : null,
            totalFrames: quality ? quality.totalVideoFrames : null,
            droppedFrames: quality ? quality.droppedVideoFrames : null,
            rect: [(rect.left - x) / width, (rect.top - y) / height,
                (rect.right - x) / width, (rect.bottom - y) / height]
        });
    }

    window.tvViewerLivePlaybackProbe = snapshot;
    return snapshot();
}
