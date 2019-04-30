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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * @author Iwao AVE!
 */
public class TypeParameterResolver {

  /**
   * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
//  解析字段的类型
  public static Type resolveFieldType(Field field, Type srcType) {
//    获取字段的声明类型
    Type fieldType = field.getGenericType();
//    获取字段定义所在的类的class对象
    Class<?> declaringClass = field.getDeclaringClass();
//    resoleType()方法进行后续处理
    return resolveType(fieldType, srcType, declaringClass);
  }

  /**
   * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type resolveReturnType(Method method, Type srcType) {
    Type returnType = method.getGenericReturnType();
    Class<?> declaringClass = method.getDeclaringClass();
    return resolveType(returnType, srcType, declaringClass);
  }

  /**
   * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the declaration,<br>
   *         they will be resolved to the actual runtime {@link Type}s.
   */
  public static Type[] resolveParamTypes(Method method, Type srcType) {
    Type[] paramTypes = method.getGenericParameterTypes();
    Class<?> declaringClass = method.getDeclaringClass();
    Type[] result = new Type[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      result[i] = resolveType(paramTypes[i], srcType, declaringClass);
    }
    return result;
  }

  private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
//    解析TypeVariable类型
    if (type instanceof TypeVariable) {
      return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
//      解析ParameterizedType类型
    } else if (type instanceof ParameterizedType) {

      return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
//      解析genericArrayType类型
    } else if (type instanceof GenericArrayType) {
      return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
    } else {
//      是class类型
      return type;
    }
//    不能直接定义成WildcardType类型，但是可以嵌套在别的类型中
  }

  /**
   *
   * @param genericArrayType
   * @param srcType
   * @param declaringClass
   * @return
   */
  private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {
    Type componentType = genericArrayType.getGenericComponentType();
//    根据数组元素类型，选择合适的方法解析
    Type resolvedComponentType = null;
    if (componentType instanceof TypeVariable) {
      resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
    } else if (componentType instanceof GenericArrayType) {
//      递归调用resovleGenericArrayType
      resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
    } else if (componentType instanceof ParameterizedType) {
      resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
    }
//    根据解析后的数组项类型，构造返回函数
    if (resolvedComponentType instanceof Class) {
      return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
    } else {
      return new GenericArrayTypeImpl(resolvedComponentType);
    }
  }

  /**
   * 解析Map<K,V>
   * @param parameterizedType 待解析的ParameterType对象
   * @param srcType  查找起始类型，TestType.SubClassA<Long>
   * @param declaringClass  声明待解析对象的Class类型
   * @return
   */
  private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
    //在该示例中，得到原始类型Map对应的Class对象
    Class<?> rawType = (Class<?>) parameterizedType.getRawType();
//    得到类型变量K,V
    Type[] typeArgs = parameterizedType.getActualTypeArguments();
//    用于保存解析后的结果
    Type[] args = new Type[typeArgs.length];
//    解析K,V
    for (int i = 0; i < typeArgs.length; i++) {
      if (typeArgs[i] instanceof TypeVariable) {
//        解析类型变量
        args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof ParameterizedType) {
//        如果嵌套了ParameterType,则调用resolveParameterizedType()方法进行处理
        args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);
      } else if (typeArgs[i] instanceof WildcardType) {
//        如果嵌套了WildcardType,则调用resolveWildcardType方法进行处理
        args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);
      } else {
        args[i] = typeArgs[i];
      }
    }
//    将解析后结果封装成TypeParameterResovler中定义的ParameterizedType实现，并返回， 本例中args数组中的元素都是Long.class
    return new ParameterizedTypeImpl(rawType, null, args);
  }

  private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
    Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
    Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
    return new WildcardTypeImpl(lowerBounds, upperBounds);
  }

  private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
    Type[] result = new Type[bounds.length];
    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i] instanceof TypeVariable) {
        result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof ParameterizedType) {
        result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
      } else if (bounds[i] instanceof WildcardType) {
        result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
      } else {
        result[i] = bounds[i];
      }
    }
    return result;
  }

  /**
   * 解析TypeVariable
   * @param typeVar K 对应的TypeVar
   * @param srcType TestType.SubClassA<Long>对应的ParameterizedType
   * @param declaringClass ClassA对应的class对象
   * @return
   */
  private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
    Type result = null;
    Class<?> clazz = null;
    if (srcType instanceof Class) {
      clazz = (Class<?>) srcType;
    } else if (srcType instanceof ParameterizedType) {
//      本例中SubClassA<Long>是ParameterizedType类型，clazz为SubClassA.classs
      ParameterizedType parameterizedType = (ParameterizedType) srcType;
      clazz = (Class<?>) parameterizedType.getRawType();
    } else {
      throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType, but was: " + srcType.getClass());
    }

    //SubClassA.class == ClassA.class -> false
    if (clazz == declaringClass) {
      Type[] bounds = typeVar.getBounds();
      if(bounds.length > 0) {
        return bounds[0];
      }
      return Object.class;
    }

//    获取声明的父类型 即Class<T,T>对应的ParamterizedType对象
    Type superclass = clazz.getGenericSuperclass();
//    通过扫描父类进行后续解析，这是递归入口
    result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclass);
    if (result != null) {
      return result;
    }

//    获取接口
    Type[] superInterfaces = clazz.getGenericInterfaces();
    for (Type superInterface : superInterfaces) {
//      通过扫描接口进行后续解析，逻辑同扫描父类
      result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterface);
      if (result != null) {
        return result;
      }
    }
//    若在整个继承中都没有找到，返回Object.class
    return Object.class;
  }

  /**
   * 递归继承结构，完成类型变量的解析
   * @param typeVar  K 对应的TypeVar
   * @param srcType TestType.SubClassA<Long>.class
   * @param declaringClass ClassA.class
   * @param clazz SubClassA.class
   * @param superclass Class<T,T>对应的ParamterizedType
   * @return
   */
  private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclass) {
    Type result = null;
    // ClassA<T,T> true
    if (superclass instanceof ParameterizedType) {
      ParameterizedType parentAsType = (ParameterizedType) superclass;
//      ClassA.class
      Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();
//      ClassA.class == ClassA.class true
      if (declaringClass == parentAsClass) {
        //获取参数 {T,T}
        Type[] typeArgs = parentAsType.getActualTypeArguments();
//        ClassA中定义的类型变量是K,V
        TypeVariable<?>[] declaredTypeVars = declaringClass.getTypeParameters();
        for (int i = 0; i < declaredTypeVars.length; i++) {
//          解析目标变量 K == K true
          if (declaredTypeVars[i] == typeVar) {
//            待解析的T是TypeVar
            if (typeArgs[i] instanceof TypeVariable) {
//              获取SubClassA的参数化类型 Long.class
              TypeVariable<?>[] typeParams = clazz.getTypeParameters();
//              变量TypePrams 这里就是Long.class 一次
              for (int j = 0; j < typeParams.length; j++) {
//                Long.class  == T(Long.class) true
                if (typeParams[j] == typeArgs[i]) {
//                  SubClassA<Long> 是ParameterizedType
                  if (srcType instanceof ParameterizedType) {
//                    获取SubClasA的参数化 类型，Long.class
                    result = ((ParameterizedType) srcType).getActualTypeArguments()[j];
                  }
                  break;
                }
              }
            } else {
//              直接返回Long.class
              result = typeArgs[i];
            }
          }
        }
      } else if (declaringClass.isAssignableFrom(parentAsClass)) {
//        继续解析父类，知道解析到定义该字段的类
        result = resolveTypeVar(typeVar, parentAsType, declaringClass);
      }
    } else if (superclass instanceof Class) {
      if (declaringClass.isAssignableFrom((Class<?>) superclass)) {
//        声明父类不在包含有类型变量且不是定义该字段的类，则继续解析
        result = resolveTypeVar(typeVar, superclass, declaringClass);
      }
    }
    return result;
  }

  private TypeParameterResolver() {
    super();
  }

  static class ParameterizedTypeImpl implements ParameterizedType {
    private Class<?> rawType;

    private Type ownerType;

    private Type[] actualTypeArguments;

    public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
      super();
      this.rawType = rawType;
      this.ownerType = ownerType;
      this.actualTypeArguments = actualTypeArguments;
    }

    @Override
    public Type[] getActualTypeArguments() {
      return actualTypeArguments;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public String toString() {
      return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments) + "]";
    }
  }

  static class WildcardTypeImpl implements WildcardType {
    private Type[] lowerBounds;

    private Type[] upperBounds;

    private WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
      super();
      this.lowerBounds = lowerBounds;
      this.upperBounds = upperBounds;
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBounds;
    }

    @Override
    public Type[] getUpperBounds() {
      return upperBounds;
    }
  }

  static class GenericArrayTypeImpl implements GenericArrayType {
    private Type genericComponentType;

    private GenericArrayTypeImpl(Type genericComponentType) {
      super();
      this.genericComponentType = genericComponentType;
    }

    @Override
    public Type getGenericComponentType() {
      return genericComponentType;
    }
  }
}
