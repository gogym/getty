package com.gettyio.core.util;

import java.io.Serializable;
import java.util.Comparator;


public final class CaseIgnoringComparator implements Comparator<String>, Serializable {

    private static final long serialVersionUID = 8426899657127533862L;

    public static final CaseIgnoringComparator INSTANCE = new CaseIgnoringComparator();

    private CaseIgnoringComparator() {
        super();
    }

    public int compare(String o1, String o2) {
        return o1.compareToIgnoreCase(o2);
    }

    private Object readResolve() {
        return INSTANCE;
    }
}