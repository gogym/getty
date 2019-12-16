package test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class AscPic {

    public static void main(String[] args) throws IOException {
        String path = "d:/789.png";//导入的图片
        String base = "m";//将会用这个字符串里的字符填充图片
        BufferedImage image = ImageIO.read(new File(path));//读入图片，并用图片缓冲区对象来接收

        //双层for循环，遍历图片
        for (int y = 0; y < image.getHeight(); y++) {//先竖向遍历，再横向遍历，即一行一行的找，后面也会一行一行的打印
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getRGB(x, y);//图片缓冲区自带的方法，可以得到当前点的颜色值，返回值是int类型
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                float gray = 0.299f * r + 0.578f * g + 0.114f * b;//灰度值计算公式，固定比例，无需理解
                int index = Math.round(gray * (base.length()) / 255);
                if (index >= base.length()) {
                    System.out.print(" ");//白色的地方打空格，相当于白色背景，这样图片轮廓比较明显
                } else {
                    System.out.print(base.charAt(index));//有颜色的地方打字符
                }
            }
            System.out.println();//一行打完，换行
        }
    }
}
