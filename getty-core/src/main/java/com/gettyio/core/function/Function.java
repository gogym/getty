/**
 * 包名：org.getty.core.function
 * 版权：Copyright by www.getty.com 
 * 描述：
 * 邮箱：189155278@qq.com
 * 时间：2019/9/27
 */
package com.gettyio.core.function;

/**
 * 这是函数式编程，基于一个输入值确定一个输出值，参考于google  Guava
 */
public interface Function<F, T> {
    T apply(F input);
    boolean equals(Object object);
}
