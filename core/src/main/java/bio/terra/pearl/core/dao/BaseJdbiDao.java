package bio.terra.pearl.core.dao;

import bio.terra.pearl.core.model.BaseEntity;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.mapper.reflect.BeanMapper;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.springframework.beans.PropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;

public abstract class BaseJdbiDao<T extends BaseEntity> {
    protected Jdbi jdbi;
    protected List<String> insertFields;
    protected List<String> insertFieldSymbols;
    protected List<String> insertColumns;

    protected List<String> getQueryFields;
    protected List<String> getQueryFieldSymbols;
    protected List<String> getQueryColumns;
    protected String createQuerySql;
    @Getter
    protected String tableName;
    protected Class<T> clazz;

    protected abstract Class<T> getClazz();

    protected RowMapper<T> getRowMapper() {
        return BeanMapper.of(getClazz());
    }
    protected RowMapper<T> getRowMapper(String prefix) {
        return BeanMapper.of(getClazz(), prefix);
    }

    protected List<String> getInsertExcludedFields() {
        return Arrays.asList("id");
    }

    protected boolean isSimpleFieldType(Class fieldType) {
        return Enum.class.isAssignableFrom(fieldType) ||
                Arrays.asList(String.class, Instant.class, LocalDate.class, Boolean.class, boolean.class,
                                Integer.class, Double.class, int.class, UUID.class, byte[].class)
                        .contains(fieldType);
    }

    /**
     * the fields to insert on are the 'get' fields without class and id, class because it's not a true
     * data property, (it's just from getClass(), and 'id' because we want id to be auto-generated
     */
    protected List<String> generateInsertFields(Class<T> clazz) {
        List<String> insertFields = generateGetFields(clazz);
        insertFields.removeAll(getInsertExcludedFields());
        return insertFields;
    }

    protected List<String> generateGetFields(Class<T> clazz) {
        try {
            BeanInfo info = Introspector.getBeanInfo(clazz);
            List<String> allSimpleProperties = Arrays.asList(info.getPropertyDescriptors()).stream()
                    .filter(descriptor -> isSimpleFieldType(descriptor.getPropertyType()))
                    .map(descriptor -> descriptor.getName())
                    .filter(name -> !name.equals("class"))
                    .collect(Collectors.toList());
            return allSimpleProperties;
        } catch (IntrospectionException e) {
            throw new RuntimeException("Unable to introspect " + getClazz().getName());
        }
    }

    protected List<String> generateInsertColumns(List<String> insertFields) {
        return insertFields.stream().map(field -> toSnakeCase(field))
                .collect(Collectors.toList());
    }

    protected List<String> generateGetColumns(List<String> insertFields) {
        return getQueryFields.stream().map(field -> toSnakeCase(field))
                .collect(Collectors.toList());
    }

    protected String generateTableName() {
        return toSnakeCase(getClazz().getSimpleName());
    };

    public BaseJdbiDao(Jdbi jdbi) {
        this.jdbi = jdbi;
        clazz = getClazz();
        insertFields = generateInsertFields(clazz);
        insertFieldSymbols = insertFields.stream().map(field -> ":" + field).collect(Collectors.toList());
        insertColumns = generateInsertColumns(insertFields);
        getQueryFields = generateGetFields(clazz);
        getQueryFieldSymbols = getQueryFields.stream().map(field -> ":" + field).collect(Collectors.toList());
        getQueryColumns = generateGetColumns(getQueryFields);
        tableName = generateTableName();
        createQuerySql = getCreateQuerySql();
        initializeRowMapper(jdbi);
    }

    protected void initializeRowMapper(Jdbi jdbi) {
        jdbi.registerRowMapper(clazz, getRowMapper());
    }

    /**
     * creates the object.  Will error if the object already has an id, as we generally want those to be auto-generated
     * and a pre-existing id likely indicates a coding error like inadvertently re-creating an already deleted object
     */
    public T create(T modelObj) {
        if (modelObj.getId() != null) {
            throw new IllegalArgumentException("object passed to create already has id - " + modelObj.getId());
        }
        return jdbi.withHandle(handle ->
                handle.createUpdate(createQuerySql)
                        .bindBean(modelObj)
                        .executeAndReturnGeneratedKeys()
                        .mapTo(clazz)
                        .one()
        );
    }

    /**
     * creation method for cases where the object's id needs to be pre-specified (such as calling out to an external service
     * prior to creation)
     */
    public T createWithIdSpecified(T modelObj) {
        return jdbi.withHandle(handle ->
                handle.createUpdate(getCreateQueryWithIdSpecifiedSql())
                        .bindBean(modelObj)
                        .executeAndReturnGeneratedKeys()
                        .mapTo(clazz)
                        .one()
        );
    }

    /**
     * creates all the objects with a single call to the database -- this has the downside that it does not
     * return the created objects --it returns an int[] with the number of rows modified -- it should be all ones
     * */
    public void bulkCreate(List<T> modelObjs) {
        if (modelObjs.isEmpty()) {
            return;
        }
        int[] result = jdbi.withHandle(handle -> {
            PreparedBatch batch = handle.prepareBatch(createQuerySql);
            for (T obj : modelObjs) {
                if (obj.getId() != null) {
                    throw new IllegalArgumentException("object passed to bulk create already has id: " + obj.getId());
                }
                batch.bindBean(obj).add();
            }
            return batch.execute();
        });
        // I can't think of any case where a create command would not update a row and also not throw an exception,
        // but just in case, we check for it here
        if (result.length != modelObjs.size() || Arrays.stream(result).anyMatch(rowsUpdated -> rowsUpdated != 1)) {
            throw new IllegalStateException("bulk create failed for at least one row");
        }
    }

    protected String getCreateQuerySql() {
        return "insert into " + tableName + " (" + StringUtils.join(insertColumns, ", ") +") " +
                "values (" + StringUtils.join(insertFieldSymbols, ", ") + ");";
    }

    /**
     * we typically do not include id in create queries as id columns are set to be auto-generated.
     * but in some cases the id needs to be generated in advance of the creation.
     */
    protected String getCreateQueryWithIdSpecifiedSql() {
        return "insert into " + tableName + " (id, " + StringUtils.join(insertColumns, ", ") +") " +
                "values (:id, " + StringUtils.join(insertFieldSymbols, ", ") + ");";
    }

    /** basic get-by-id */
    public Optional<T> find(UUID id) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from " + tableName + " where id = :id;")
                        .bind("id", id)
                        .mapTo(clazz)
                        .findOne()
        );
    }

    public List<T> findAll() {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from " + tableName)
                        .mapTo(clazz)
                        .list()
        );
    }

    /** finds all the entities by the given ids */
    public List<T> findAll(List<UUID> uuids) {
        return findAllByPropertyCollection("id", uuids);
    }

    /**
     * Fetches an entity with a child attached.  For example, if the parent table has a column "mailing_address_id" and
     * a field mailingAddress, this method could be used to fetch the parent with the mailing address already hydrated
     * and do so in a single SQL query
     */
    protected Optional<T> findWithChild(UUID id, String childIdPropertyName, String childPropertyName, BaseJdbiDao childDao) {
        List<String> parentCols = getQueryColumns.stream().map(col -> "a." + col + " a_" + col)
                .collect(Collectors.toList());
        List<String> childCols = ((List<String>) childDao.getQueryColumns).stream().map(col -> "b." + col + " b_" + col)
                .collect(Collectors.toList());
        return jdbi.withHandle(handle ->
                handle.createQuery("select " + String.join(", ", parentCols) + ", "
                        + String.join(", ", childCols)
                        + " from " + tableName + " a left join " + childDao.tableName
                        + " b on a." + toSnakeCase(childIdPropertyName) + " = b.id"
                        + " where a.id = :id")
                        .bind("id", id )
                        .registerRowMapper(clazz, getRowMapper("a"))
                        .registerRowMapper(childDao.clazz, childDao.getRowMapper("b"))
                        .reduceRows((Map<UUID, T> map, RowView rowView) -> {
                            T parent = map.computeIfAbsent(
                                    rowView.getColumn("a_id", UUID.class),
                                    rowId -> rowView.getRow(clazz));
                            if (rowView.getColumn("b_id", UUID.class) != null) {
                                PropertyAccessor accessor = PropertyAccessorFactory.forBeanPropertyAccess(parent);
                                accessor.setPropertyValue(childPropertyName, rowView.getRow(childDao.getClazz()));
                            }
                        })
                        .findFirst()
        );
    }

    protected Optional<T> findByProperty(String columnName, Object columnValue) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from " + tableName + " where " + columnName + " = :columnValue;")
                        .bind("columnValue", columnValue)
                        .mapTo(clazz)
                        .findOne()
        );
    }

    protected Optional<T> findByTwoProperties(String column1Name, Object column1Value,
                                              String column2Name, Object column2Value) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from " + tableName + " where " + column1Name + " = :column1Value"
                        + " and " + column2Name + " = :column2Value;")
                        .bind("column1Value", column1Value)
                        .bind("column2Value", column2Value)
                        .mapTo(clazz)
                        .findOne()
        );
    }

    protected List<T> findAllByTwoProperties(String column1Name, Object column1Value,
                                              String column2Name, Object column2Value) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from " + tableName + " where " + column1Name + " = :column1Value"
                                + " and " + column2Name + " = :column2Value;")
                        .bind("column1Value", column1Value)
                        .bind("column2Value", column2Value)
                        .mapTo(clazz)
                        .list()
        );
    }

    protected List<T> findAllByProperty(String columnName, Object columnValue) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from " + tableName + " where " + columnName + " = :columnValue;")
                        .bind("columnValue", columnValue)
                        .mapTo(clazz)
                        .list()
        );
    }

    protected Stream<T> streamAllByProperty(String columnName, Object columnValue) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from " + tableName + " where " + columnName + " = :columnValue;")
                        .bind("columnValue", columnValue)
                        .mapTo(clazz)
                        .stream()
        );
    }

    protected List<T> findAllByPropertyCollection(String columnName, Collection<?> columnValues) {
        if (columnValues.isEmpty()) {
            // short circuit this case because bindList errors if list is empty
            return new ArrayList<>();
        }
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from " + tableName + " where " + columnName + " IN (<columnValues>);")
                        .bindList("columnValues", columnValues)
                        .mapTo(clazz)
                        .list()
        );
    }

    protected Stream<T> streamAllByPropertyCollection(String columnName, Collection<?> columnValues) {
        if (columnValues.isEmpty()) {
            // short circuit this case because bindList errors if list is empty
            return Stream.empty();
        }
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from " + tableName + " where " + columnName + " IN (<columnValues>);")
                        .bindList("columnValues", columnValues)
                        .mapTo(clazz)
                        .stream()
        );
    }

    protected List<T> findAllByPropertySorted(String columnName, Object columnValue, String sortProperty, String sortDir) {
        return jdbi.withHandle(handle ->
                handle.createQuery("select * from " + tableName + " where " + columnName + " = :columnValue"
                        + " order by " + sortProperty + " " + sortDir)
                        .bind("columnValue", columnValue)
                        .mapTo(clazz)
                        .list()
        );
    }

    protected List<T> findAllByTwoPropertiesSorted(String column1Name, Object column1Value,
                                                   String column2Name, Object column2Value,
                                                   String sortProperty, String sortDir) {
        return jdbi.withHandle(handle ->
                handle.createQuery("""
                                select * from %s 
                                where %s = :column1Value
                                and %s = :column2Value
                                order by %s %s
                                """.formatted(tableName, column1Name, column2Name, sortProperty, sortDir))
                        .bind("column1Value", column1Value)
                        .bind("column2Value", column2Value)
                        .mapTo(clazz)
                        .list()
        );
    }

    /** defaults to matching on id if provided. */
    public Optional<T> findOneMatch(T matchObj) {
        if (matchObj.getId() != null) {
            return find(matchObj.getId());
        }
        return Optional.empty();
    }

    public void delete(UUID id) {
        jdbi.withHandle(handle ->
                handle.createUpdate("delete from " + tableName + " where id = :id;")
                        .bind("id", id)
                        .execute()
        );
    }

    public void deleteAll(List<UUID> ids) {
        if (ids.isEmpty()) { return; } // bindList does not allow empty lists
        jdbi.withHandle(handle ->
                handle.createUpdate("delete from " + tableName + " where id IN (<ids>);")
                        .bindList("ids", ids)
                        .execute()
        );
    }

    protected void deleteByProperty(String columnName, Object columnValue) {
        jdbi.withHandle(handle ->
                handle.createUpdate("delete from " + tableName + " where " + columnName + " = :propertyValue;")
                        .bind("propertyValue", columnValue)
                        .execute()
        );
    }

    protected void deleteByParentUuid(String parentColumnName, UUID parentUUID, BaseJdbiDao parentDao) {
        jdbi.withHandle(handle ->
                handle.createUpdate("delete from " + tableName + " using  " + parentDao.tableName
                        + " where " + tableName + ".id = " + parentDao.tableName + "." + parentColumnName
                        + " and " + parentColumnName + " = :parentUUID;")
                        .bind("parentUUID", parentUUID)
                        .execute()
        );
    }

    public int count() {
        return jdbi.withHandle(handle -> handle
                .createQuery("select count(1) from " + tableName)
                .mapTo(int.class)
                .one()
        );
    }

    protected int countByProperty(String propertyName, Object value) {
        return jdbi.withHandle(handle -> handle.createQuery("select count(1) from " + tableName
                + " where " + propertyName + " = :value")
                .bind("value", value)
                .mapTo(Integer.class)
                .one()
        );
    }

    protected String prefixedGetQueryColumns(String prefix) {
        List<String> prefixedCols = getQueryColumns.stream().map(col -> prefix + "." + col)
                .collect(Collectors.toList());
        return StringUtils.join(prefixedCols, ", ");
    }

    // from https://stackoverflow.com/questions/10310321/regex-for-converting-camelcase-to-camel-case-in-java
    public static String toSnakeCase(String camelCased) {
        return camelCased.replaceAll("(.)(\\p{Upper})", "$1_$2").toLowerCase();
    }

    /** returns a cloned list of the get query columns (the actual list should never be modified) */
    public List<String> getGetQueryColumns() {
        List<String> copy = new ArrayList<>();
        copy.addAll(getQueryColumns);
        return copy;
    }

}
