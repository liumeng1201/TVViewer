package com.eanyatonic.cctvViewer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class ChannelCatalog {
    enum Source {
        CCTV("央视频道", "cctv"),
        YANGSHIPIN("地方频道", "yangshipin");

        final String groupName;
        final String id;

        Source(String groupName, String id) {
            this.groupName = groupName;
            this.id = id;
        }
    }

    static final class Channel {
        final int number;
        final String id;
        final String name;
        final String url;
        final Source source;

        Channel(int number, String route, String name, String url, Source source) {
            this.number = number;
            this.id = source.id + ":" + route;
            this.name = name;
            this.url = url;
            this.source = source;
        }

        String label() {
            return number + " " + name;
        }

        int index() {
            return number - 1;
        }
    }

    private static final List<Channel> ALL = Collections.unmodifiableList(Arrays.asList(
            cctv(1, "CCTV-1 综合", "cctv1/"),
            cctv(2, "CCTV-2 财经", "cctv2/"),
            cctv(3, "CCTV-3 综艺", "cctv3/"),
            cctv(4, "CCTV-4 中文国际（亚）", "cctv4/"),
            cctv(5, "CCTV-5 体育", "cctv5/"),
            cctv(6, "CCTV-6 电影", "cctv6/"),
            cctv(7, "CCTV-7 国防军事", "cctv7/"),
            cctv(8, "CCTV-8 电视剧", "cctv8/"),
            cctv(9, "CCTV-9 纪录", "cctvjilu"),
            cctv(10, "CCTV-10 科教", "cctv10/"),
            cctv(11, "CCTV-11 戏曲", "cctv11/"),
            cctv(12, "CCTV-12 社会与法", "cctv12/"),
            cctv(13, "CCTV-13 新闻", "cctv13/"),
            cctv(14, "CCTV-14 少儿", "cctvchild"),
            cctv(15, "CCTV-15 音乐", "cctv15/"),
            cctv(16, "CCTV-16 奥林匹克", "cctv16/"),
            cctv(17, "CCTV-17 农业农村", "cctv17/"),
            cctv(18, "CCTV-5+ 体育赛事", "cctv5plus/"),
            cctv(19, "CCTV-4 中文国际（欧）", "cctveurope"),
            cctv(20, "CCTV-4 中文国际（美）", "cctvamerica/"),
            provincial(21, "北京卫视", "600002309"),
            provincial(22, "江苏卫视", "600002521"),
            provincial(23, "东方卫视", "600002483"),
            provincial(24, "浙江卫视", "600002520"),
            provincial(25, "湖南卫视", "600002475"),
            provincial(26, "湖北卫视", "600002508"),
            provincial(27, "广东卫视", "600002485"),
            provincial(28, "广西卫视", "600002509"),
            provincial(29, "黑龙江卫视", "600002498"),
            provincial(30, "海南卫视", "600002506"),
            provincial(31, "重庆卫视", "600002531"),
            provincial(32, "深圳卫视", "600002481"),
            provincial(33, "四川卫视", "600002516"),
            provincial(34, "河南卫视", "600002525"),
            provincial(35, "福建东南卫视", "600002484"),
            provincial(36, "贵州卫视", "600002490"),
            provincial(37, "江西卫视", "600002503"),
            provincial(38, "辽宁卫视", "600002505"),
            provincial(39, "安徽卫视", "600002532"),
            provincial(40, "河北卫视", "600002493"),
            provincial(41, "山东卫视", "600002513")
    ));
    static final List<Channel> CCTV = group(Source.CCTV);
    static final List<Channel> PROVINCIAL = group(Source.YANGSHIPIN);

    private ChannelCatalog() {
    }

    static int size() {
        return ALL.size();
    }

    static Channel at(int index) {
        return ALL.get(index);
    }

    static Channel byNumber(int number) {
        return number < 1 || number > size() ? null : at(number - 1);
    }

    static Channel byId(String id) {
        for (Channel channel : ALL) {
            if (channel.id.equals(id)) {
                return channel;
            }
        }
        return null;
    }

    private static List<Channel> group(Source source) {
        List<Channel> result = new ArrayList<>();
        for (Channel channel : ALL) {
            if (channel.source == source) {
                result.add(channel);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static Channel cctv(int number, String name, String route) {
        String url = "https://tv.cctv.com/live/" + route;
        return new Channel(number, route.replace("/", ""), name, url, Source.CCTV);
    }

    private static Channel provincial(int number, String name, String pid) {
        return new Channel(number, pid, name, "https://www.yangshipin.cn/tv/home?pid=" + pid,
                Source.YANGSHIPIN);
    }
}
