package org.apache.ibatis.mytest.reflection;

import java.util.Map;

/**
 * Created by 召君王 on 2019/2/27.
 */
public class ClassA<K,V> {
    protected Map<K,V> map;

    public Map<K, V> getMap() {
        return map;
    }

    public void setMap(Map<K, V> map) {
        this.map = map;
    }
}
