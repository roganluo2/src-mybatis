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
package org.apache.ibatis.binding;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
//封装mapper对应的method信息 以及对应的sql语句
//  mapper接口以及映射配置文件中定义的sql语句的桥梁
public class MapperMethod {

//  记录了sql语句的名称和类型
  private final SqlCommand command;
//  mapper接口中对应的相关信息
  private final MethodSignature method;

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
//    根据sql语句的类型调用sqlsession对应的方法
    switch (command.getType()) {
      case INSERT: {
//        使用paramnameresolver处理argsp[]数组，将用户传入的参数与指定参数名称关联起来
    	Object param = method.convertArgsToSqlCommandParam(args);
//        调用sqlsession.insert方法，rowcountresult方法会根据method字段中记录的方法的返回值类型对结果进行转换
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
//        update delete类型的sql语句处理与insert类型的sql语句类似，唯一的区别是调用了sqlsession和update（）方法 delete（）方法
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
//        处理返回值void且resultset通过resulthandler处理方法
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
        } else if (method.returnsMany()) {
//          处理返回值为集合或数组的方法
          result = executeForMany(sqlSession, args);

        } else if (method.returnsMap()) {
//          处理返回值为map的方法
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
//          处理返回值为cursor的方法
          result = executeForCursor(sqlSession, args);
        } else {
//          处理返回值为单一对象的方法
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName() 
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }

  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
//      mapper接口中相应方法返回值为void
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
//      mapper接口中相应方法的返回值为int或者integer
      result = rowCount;
//      mapper接口中相应方法的返回值为long或者Long
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      result = (long)rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      result = rowCount > 0;
    } else {
//      以上条件都不成立，异常
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
//    获取sql语句对应的mappedstatement对象，mappedstatement中记录了sql语句相关信息
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
//    使用resultHashmap处理结果集是，必须制定resultMap resultType
    if (void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName() 
          + " needs either a @ResultMap annotation, a @ResultType annotation," 
          + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
//    转换实参列表
    Object param = method.convertArgsToSqlCommandParam(args);
//    检查参数列表中是否有rowbounds类型参数
    if (method.hasRowBounds()) {
//      获取rowbounds对象，根据methodsignature.rowboundsindex字段制定位置，从args数组中查找
      RowBounds rowBounds = method.extractRowBounds(args);
//      调用sqlsession.select()方法。执行查询，并由指定的resulthandler处理结果对象
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  /**
   * 处理返回值为数组或者collection接口实现类
   * @param sqlSession
   * @param args
   * @param <E>
   * @return
   */
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
//    参数列表转换
    Object param = method.convertArgsToSqlCommandParam(args);
//    检测是否指定了rowbounds
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
//      调用sqlsession.selectList方法完成查询
      result = sqlSession.<E>selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.<E>selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
//    将结果转换成数组或者collection集合
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.<T>selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.<T>selectCursor(command.getName(), param);
    }
    return result;
  }

  /**
   * 转换为collection
   * @param config
   * @param list
   * @param <E>
   * @return
   */
  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
//    使用前面介绍的objectfactory通过反射创建集合对象
    Object collection = config.getObjectFactory().create(method.getReturnType());
//    创建metaObject
    MetaObject metaObject = config.newMetaObject(collection);
//    实际上调用collection.addall方法
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
//    获取数组元素类型
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
//    创建数组对象
    Object array = Array.newInstance(arrayComponentType, list.size());
//    将list中每一项添加到数组中
    if (arrayComponentType.isPrimitive()) {
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    } else {
      return list.toArray((E[])array);
    }
  }

  /**
   * 返回map
   * @param sqlSession
   * @param args
   * @param <K>
   * @param <V>
   * @return
   */
  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
//    转换实参列表
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
//      调用sqlsession.selectMap方法，完成查询操作
      result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  public static class SqlCommand {

//    sql语句的名称
    private final String name;
//    枚举类型
    private final SqlCommandType type;

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      final String methodName = method.getName();
      final Class<?> declaringClass = method.getDeclaringClass();
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
          configuration);
      if (ms == null) {
        if (method.getAnnotation(Flush.class) != null) {
          name = null;
          type = SqlCommandType.FLUSH;
        } else {
          throw new BindingException("Invalid bound statement (not found): "
              + mapperInterface.getName() + "." + methodName);
        }
      } else {
//        初始化name type
        name = ms.getId();
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
        Class<?> declaringClass, Configuration configuration) {
//      sql语句的名称是有mapper接口的名称与对应的方法名称组成的
      String statementId = mapperInterface.getName() + "." + methodName;
//      检测是否有该名称的sql语句
      if (configuration.hasStatement(statementId)) {
//        从configuration.mappedstatements集合中查找对应的mappedstatement对象，
//        mapperstatement对象封装了sql语句相关的信息，在mybatis初始化的时候创建
        return configuration.getMappedStatement(statementId);
      } else if (mapperInterface.equals(declaringClass)) {
        return null;
      }
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
//        如果方法是在父接口中定义的，则在此进行集成结构的处理
        if (declaringClass.isAssignableFrom(superInterface)) {
//          从configuration。mappedstatement集合中查找对应的mappedstatement对象
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
              declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

//  封装了mapper接口定义的方法相关信息
  public static class MethodSignature {

//  返回值类型是否为collection类型或者数组类型
    private final boolean returnsMany;
//  返回值类型是否为map类型
    private final boolean returnsMap;
//  返回值是否为void
    private final boolean returnsVoid;
//  返回值是否为cursor类型
    private final boolean returnsCursor;
//  返回值类型
    private final Class<?> returnType;
//  如果返回值是map，则该字段记录了作为key的列名
    private final String mapKey;
//    用来标记该方法参数列表中resulthandler参数类型的位置
    private final Integer resultHandlerIndex;
//  用来标记该方法参数列表中rowbounds类型参数的位置
    private final Integer rowBoundsIndex;
//  对应paramNameresolver
    private final ParamNameResolver paramNameResolver;

    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
//      解析方法的返回值类型
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        this.returnType = method.getReturnType();
      }
//      初始化字段
      this.returnsVoid = void.class.equals(this.returnType);
      this.returnsMany = (configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray());
      this.returnsCursor = Cursor.class.equals(this.returnType);
//      如methodsignature对应方法的返回值是map，且指定了mapkey注解，则使用getmapkey方法处理
      this.mapKey = getMapKey(method);
      this.returnsMap = (this.mapKey != null);
//      初始化rowboundsindex和resulthandlerindex字段
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
//      创建paramnameresolver
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }

//  负责将args[]数组转换成sql语句对应的参数列表，它通过上面介绍的paranameresolver.getNameparams()实现
    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public String getMapKey() {
      return mapKey;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes();
//      遍历methodsignature对应方法的参数列表
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) {
//          记录paramtype参数在参数列表中的位置索引
          if (index == null) {
            index = i;
          } else {
//            rowbounds和resulthandler参数只能有一个，不能重复出现
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }

    private String getMapKey(Method method) {
      String mapKey = null;
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }
  }

}
