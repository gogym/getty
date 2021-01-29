package test;

import com.gettyio.core.util.FastArrayList;
import com.gettyio.core.util.FastCopyOnWriteArrayList;

public class TestArray {

    public static void main(String[] args) {

        testFastCopyOnWriteArrayList();
    }


    public static void testFastArrayList() {
        FastArrayList<Integer> fastArrayList = new FastArrayList<>();
        fastArrayList.add(1);
        fastArrayList.add(2);
        fastArrayList.add(3);
        fastArrayList.add(4);
        fastArrayList.add(5);
        fastArrayList.add(6);
        fastArrayList.add(7);
        fastArrayList.add(8);
        fastArrayList.add(9);
        fastArrayList.add(10);

        fastArrayList.add(5, 11);
        fastArrayList.addFirst(33);
        fastArrayList.addLast(55);
        for (int i = 0; i < fastArrayList.size(); i++) {
            System.out.println(fastArrayList.get(i));
        }
        System.out.println(fastArrayList.getFirst());
        System.out.println(fastArrayList.getLast());
    }


    public static void testFastCopyOnWriteArrayList() {

        FastCopyOnWriteArrayList<Integer> fastCopyOnWriteArrayList = new FastCopyOnWriteArrayList<>();

        for (int i = 1; i <= 100; i++) {
            fastCopyOnWriteArrayList.add(i);
        }

//        for (int i = 0; i < 100; i++) {
//            Integer ii = fastCopyOnWriteArrayList.get(i);
//            System.out.println(ii);
//        }



        for (int i = 0; i < fastCopyOnWriteArrayList.size(); i++) {
            Integer ii = fastCopyOnWriteArrayList.get(i);
            System.out.println(ii);

            if(ii==30){
                fastCopyOnWriteArrayList.remove(ii);
                fastCopyOnWriteArrayList.add(666666);
                fastCopyOnWriteArrayList.set(50,5555);

            }

        }



//        System.out.println(fastCopyOnWriteArrayList.get(50));
//        System.out.println(fastCopyOnWriteArrayList.getFirst());
//        System.out.println(fastCopyOnWriteArrayList.getLast());

        //插入指定位置
//        fastCopyOnWriteArrayList.add(50,101);
//        System.out.println(fastCopyOnWriteArrayList.get(50));
//        System.out.println(fastCopyOnWriteArrayList.size());
//        System.out.println(fastCopyOnWriteArrayList.get(51));

        //替换，长度不变
//        fastCopyOnWriteArrayList.set(50,6666);
//        System.out.println(fastCopyOnWriteArrayList.get(50));
//        System.out.println(fastCopyOnWriteArrayList.size());

    }

}
