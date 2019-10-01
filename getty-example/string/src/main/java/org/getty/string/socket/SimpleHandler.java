package org.getty.string.socket;


import org.getty.core.channel.AioChannel;
import org.getty.core.pipeline.PipelineDirection;
import org.getty.core.pipeline.in.SimpleChannelInboundHandler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class SimpleHandler extends SimpleChannelInboundHandler<String> {

    int i = 0;


    @Override
    public void channelAdded(AioChannel aioChannel) {

        System.out.println("连接过来了");


    }

    @Override
    public void channelClosed(AioChannel aioChannel) {
        System.out.println("连接关闭了");
    }


    @Override
    public void channelRead0(AioChannel aioChannel, String str) {

        i++;
        //System.out.println("读取消息:" + i + "条");

//        try {
//            System.out.println("读取消息了:" + str);
//
//            String s = "汉皇重色思倾国，御宇多年求不得。\r\n" +
//                    "杨家有女初长成，养在深闺人未识。\r\n" +
//                    "天生丽质难自弃，一朝选在君王侧。\r\n" +
//                    "回眸一笑百媚生，六宫粉黛无颜色。\r\n" +
//                    "春寒赐浴华清池，温泉水滑洗凝脂。\r\n" +
//                    "侍儿扶起娇无力，始是新承恩泽时。\r\n" +
//                    "云鬓花颜金步摇，芙蓉帐暖度春宵。\r\n" +
//                    "春宵苦短日高起，从此君王不早朝。\r\n" +
//                    "承欢侍宴无闲暇，春从春游夜专夜。\r\n" +
//                    "后宫佳丽三千人，三千宠爱在一身。\r\n" +
//                    "金屋妆成娇侍夜，玉楼宴罢醉和春。\r\n" +
//                    "姊妹弟兄皆列土，可怜光彩生门户。\r\n" +
//                    "遂令天下父母心，不重生男重生女。\r\n" +
//                    "骊宫高处入青云，仙乐风飘处处闻。\r\n" +
//                    "缓歌慢舞凝丝竹，尽日君王看不足。\r\n" +
//                    "渔阳鼙鼓动地来，惊破霓裳羽衣曲。\r\n" +
//                    "九重城阙烟尘生，千乘万骑西南行。\r\n" +
//                    "翠华摇摇行复止，西出都门百余里。\r\n" +
//                    "六军不发无奈何，宛转蛾眉马前死。\r\n" +
//                    "花钿委地无人收，翠翘金雀玉搔头。\r\n" +
//                    "君王掩面救不得，回看血泪相和流。\r\n" +
//                    "黄埃散漫风萧索，云栈萦纡登剑阁。\r\n" +
//                    "峨嵋山下少人行，旌旗无光日色薄。\r\n" +
//                    "蜀江水碧蜀山青，圣主朝朝暮暮情。\r\n" +
//                    "行宫见月伤心色，夜雨闻铃肠断声。\r\n" +
//                    "天旋地转回龙驭，到此踌躇不能去。\r\n" +
//                    "马嵬坡下泥土中，不见玉颜空死处。\r\n" +
//                    "君臣相顾尽沾衣，东望都门信马归。\r\n" +
//                    "归来池苑皆依旧，太液芙蓉未央柳。\r\n" +
//                    "芙蓉如面柳如眉，对此如何不泪垂。\r\n" +
//                    "春风桃李花开日，秋雨梧桐叶落时。\r\n" +
//                    "西宫南内多秋草，落叶满阶红不扫。\r\n" +
//                    "梨园弟子白发新，椒房阿监青娥老。\r\n" +
//                    "夕殿萤飞思悄然，孤灯挑尽未成眠。\r\n" +
//                    "迟迟钟鼓初长夜，耿耿星河欲曙天。\r\n" +
//                    "鸳鸯瓦冷霜华重，翡翠衾寒谁与共。\r\n" +
//                    "悠悠生死别经年，魂魄不曾来入梦。\r\n" +
//                    "临邛道士鸿都客，能以精诚致魂魄。\r\n" +
//                    "为感君王辗转思，遂教方士殷勤觅。\r\n" +
//                    "排空驭气奔如电，升天入地求之遍。\r\n" +
//                    "上穷碧落下黄泉，两处茫茫皆不见。\r\n" +
//                    "忽闻海上有仙山，山在虚无缥渺间。\r\n" +
//                    "楼阁玲珑五云起，其中绰约多仙子。\r\n" +
//                    "中有一人字太真，雪肤花貌参差是。\r\n" +
//                    "金阙西厢叩玉扃，转教小玉报双成。\r\n" +
//                    "闻道汉家天子使，九华帐里梦魂惊。\r\n" +
//                    "揽衣推枕起徘徊，珠箔银屏迤逦开。\r\n" +
//                    "云鬓半偏新睡觉，花冠不整下堂来。\r\n" +
//                    "风吹仙袂飘飖举，犹似霓裳羽衣舞。\r\n" +
//                    "玉容寂寞泪阑干，梨花一枝春带雨。\r\n" +
//                    "含情凝睇谢君王，一别音容两渺茫。\r\n" +
//                    "昭阳殿里恩爱绝，蓬莱宫中日月长。\r\n" +
//                    "回头下望人寰处，不见长安见尘雾。\r\n" +
//                    "惟将旧物表深情，钿合金钗寄将去。\r\n" +
//                    "钗留一股合一扇，钗擘黄金合分钿。\r\n" +
//                    "但教心似金钿坚，天上人间会相见。\r\n" +
//                    "临别殷勤重寄词，词中有誓两心知。\r\n" +
//                    "七月七日长生殿，夜半无人私语时。\r\n" +
//                    "在天愿作比翼鸟，在地愿为连理枝。\r\n" +
//                    "天长地久有时尽，此恨绵绵无绝期。\r\n";
//
//            byte[] msgBody = (i + "\r\n").getBytes("utf-8");
//            aioChannel.writeAndFlush(msgBody);
//            i++;
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    @Override
    public void exceptionCaught(AioChannel aioChannel, Throwable cause, PipelineDirection pipelineDirection) {
        System.out.println("出错了");
    }


    public byte[] getByteArray(int i) {
        byte[] b = new byte[4];
        b[0] = (byte) ((i & 0xff000000) >> 24);
        b[1] = (byte) ((i & 0x00ff0000) >> 16);
        b[2] = (byte) ((i & 0x0000ff00) >> 8);
        b[3] = (byte) (i & 0x000000ff);
        return b;
    }

}
