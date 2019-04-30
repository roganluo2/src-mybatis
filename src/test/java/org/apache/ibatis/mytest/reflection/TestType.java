package org.apache.ibatis.mytest.reflection;

import org.apache.ibatis.reflection.TypeParameterResolver;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Field;
import java.lang.reflect.Type;

/**
 * Created by 召君王 on 2019/2/27.
 */
public class TestType {

    SubClassA<Long> sa = new SubClassA<Long>();

    public static void main(String[] args) throws NoSuchFieldException {
        Field f = ClassA.class.getDeclaredField("map");

        Type type = TypeParameterResolver.resolveFieldType(f, ParameterizedTypeImpl.make(SubClassA.class, new Type[]{Long.class}, TestType.class));
        System.out.println(type.getClass());


    }


}
