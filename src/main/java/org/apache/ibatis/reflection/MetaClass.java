/**
 *    Copyright ${license.git.copyrightYears} the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
//通过Reflector和PropertyTokenizer组合使用，实现对复杂表达式的解析
//并实现了获取指定属性描述信息的功能
public class MetaClass {

//  用于缓存Reflector对象，
  private final ReflectorFactory reflectorFactory;
//  在创建metaclass会指定一个类，reflector记录了类的元信息
  private final Reflector reflector;

//  private修饰
  private MetaClass(Class<?> type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
//    创建reflector对象，
    this.reflector = reflectorFactory.findForClass(type);
  }

//  使用静态方法创建metaclass对象
  public static MetaClass forClass(Class<?> type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  public MetaClass metaClassForProperty(String name) {
//    查找指定属性对应的class
    Class<?> propType = reflector.getGetterType(name);
//    为该属性创建对应的metaclass对象
    return MetaClass.forClass(propType, reflectorFactory);
  }

  public String findProperty(String name) {
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      name = name.replace("_", "");
    }
    return findProperty(name);
  }

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    } else {
      return reflector.getSetterType(prop.getName());
    }
  }

  //逻辑同hasgetter类似
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
//      构建metaclass对象
      MetaClass metaProp = metaClassForProperty(prop);
//      递归
      return metaProp.getGetterType(prop.getChildren());
    }
    // issue #506. Resolve the type inside a Collection Object
//    调用重载方法
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
//    针对propertytokenizer中是否包含索引信息做进一步处理
//    获取表达式所表示的属性类型
    Class<?> propType = getGetterType(prop);
    return MetaClass.forClass(propType, reflectorFactory);
  }


  private Class<?> getGetterType(PropertyTokenizer prop) {
//    获取属性类型
    Class<?> type = reflector.getGetterType(prop.getName());
//    该表达式是否使用了[]下标，且是collection的子类
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(type)) {
//      通过TyperParameterResolver工具解析属性的类型
      Type returnType = getGenericGetterType(prop.getName());
//      针对parameterizedType进行处理
      if (returnType instanceof ParameterizedType) {
//        获取实际的类型参数
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
//          泛型的类型
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            type = (Class<?>) returnType;
          } else if (returnType instanceof ParameterizedType) {
            type = (Class<?>) ((ParameterizedType) returnType).getRawType();
          }
        }
      }
    }
    return type;
  }


  private Type getGenericGetterType(String propertyName) {
    try {
//      根据reflector.getMethods集合中记录的Invoker实现类的类型，决定解析getter方法返回值类型还是解析字段类型
      Invoker invoker = reflector.getGetInvoker(propertyName);
      if (invoker instanceof MethodInvoker) {
        Field _method = MethodInvoker.class.getDeclaredField("method");
        _method.setAccessible(true);
        Method method = (Method) _method.get(invoker);
        return TypeParameterResolver.resolveReturnType(method, reflector.getType());
      } else if (invoker instanceof GetFieldInvoker) {
        Field _field = GetFieldInvoker.class.getDeclaredField("field");
        _field.setAccessible(true);
        Field field = (Field) _field.get(invoker);
        return TypeParameterResolver.resolveFieldType(field, reflector.getType());
      }
    } catch (NoSuchFieldException e) {
    } catch (IllegalAccessException e) {
    }
    return null;
  }

  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      if (reflector.hasSetter(prop.getName())) {
        MetaClass metaProp = metaClassForProperty(prop.getName());
        return metaProp.hasSetter(prop.getChildren());
      } else {
        return false;
      }
    } else {
      return reflector.hasSetter(prop.getName());
    }
  }

//  判断属性表达式所表示的属性在class中是否存在
  public boolean hasGetter(String name) {
//    解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
//    存在待处理的子表达式
    if (prop.hasNext()) {
//      PropertyTokenizer.name指定属性有getter方法，才能处理子表达式
      if (reflector.hasGetter(prop.getName())) {
//        创建属性的metaclass
        MetaClass metaProp = metaClassForProperty(prop);
//        递归入口
        return metaProp.hasGetter(prop.getChildren());
      } else {
//        递归出口
        return false;
      }
    } else {
      return reflector.hasGetter(prop.getName());
    }
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

//  通过propertyTokenizer解析复杂的属性表达式
  private StringBuilder buildProperty(String name, StringBuilder builder) {
//    解析属性表达式
    PropertyTokenizer prop = new PropertyTokenizer(name);
//    是否还有子表达式
    if (prop.hasNext()) {
//      查找propertyTokenizer.name对应的属性
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
//        追加属性名
        builder.append(propertyName);
        builder.append(".");
//        为该属性创建metaclass对象
        MetaClass metaProp = metaClassForProperty(propertyName);
//        递归解析PropertyTokenizer.children字段，并将解析的结果添加到builder中保存
        metaProp.buildProperty(prop.getChildren(), builder);
      }
//      递归出口
    } else {
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
