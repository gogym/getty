package test;

import com.gettyio.core.util.FastArrayList;

public class TestArray {

    public static void main(String[] args) {

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

        fastArrayList.add(5,11);

        fastArrayList.addFirst(33);

        fastArrayList.addLast(55);

        for (int i=0;i<fastArrayList.size();i++){

            System.out.println(fastArrayList.get(i));
        }


        System.out.println(fastArrayList.getFirst());

        System.out.println(fastArrayList.getLast());

    }
}
