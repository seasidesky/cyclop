package org.cyclop.model;

/**
 * cql keywords are: create keyspace, use, alter keyspace, drop keyspace, create table, alter table, drop table,
 * truncate, create index, drop index, insert, update, delete, batch, select
 *
 * @author Maciej Miklas
 */
public class CqlKeyword extends CqlPart {

    public final String valueSp;

    public static enum Def {
        FROM("from"), DELETE("delete"), DROP("drop table"), INSERT_INTO("insert into"), INSERT("insert"),
        UPDATE("update"),
        WHERE("where"), USING_TIMESTAMP("using  timestamp"), USING_TTL("using ttl"), ORDER_BY("order by"),
        ASC("asc"), DESC("desc"),
        LIMIT("limit"), ALLOW_FILTERING("allow filtering"), TOKEN("token"), IN("in"), AND("and"), IN_BL("in ("),
        DROP_TABLE("drop table"), VALUES("values"), SELECT("select"), COUNT_AST("count (*)"), COUNT_ONE("count (1)"),
        WRITETIME("writetime"), TTL("ttl"),ORDER_BY_BL("order by ("),SET("set"),USE("use"), DROP_KEYSPACE("drop keyspace");

        private Def(String value) {
            this.value = new CqlKeyword(value);
        }

        public CqlKeyword value;
    }

    protected CqlKeyword(String val) {
        super(val);
        this.valueSp = val + " ";
    }

    @Override
    public String toString() {
        return "CqlKeyword{" + "part='" + part + '\'' + '}';
    }

    @Override
    public CqlType type() {
        return CqlType.KEYWORD;
    }
}