package org.codejargon.fluentjdbc.internal.query;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.codejargon.fluentjdbc.api.FluentJdbcException;
import org.codejargon.fluentjdbc.api.FluentJdbcSqlException;
import org.codejargon.fluentjdbc.api.integration.ConnectionProvider;
import org.codejargon.fluentjdbc.api.query.BatchQuery;
import org.codejargon.fluentjdbc.api.query.Query;
import org.codejargon.fluentjdbc.api.query.SelectQuery;
import org.codejargon.fluentjdbc.api.query.UpdateQuery;
import org.codejargon.fluentjdbc.internal.integration.QueryConnectionReceiverInternal;
import org.codejargon.fluentjdbc.internal.query.namedparameter.NamedSqlAndParams;

public class QueryInternal implements Query {

    final ConnectionProvider connectionProvider;
    final QueryConfig config;

    public QueryInternal(
            ConnectionProvider connectionProvider, 
            QueryConfig config
    ) {
        this.connectionProvider = connectionProvider;
        this.config = config;
    }

    @Override
    public SelectQuery select(String sql) {
        return new SelectQueryInternal(sql, this);
    }

    @Override
    public UpdateQuery update(String sql) {
        return new UpdateQueryInternal(sql, this);
    }

    @Override
    public BatchQuery batch(String sql) {
        return new BatchQueryInternal(sql, this);
    }

    <T> T query(QueryRunner<T> runner, String sql) {
        try {
            QueryConnectionReceiverInternal<T> receiver = new QueryConnectionReceiverInternal<>(runner);
            connectionProvider.provide(receiver);
            return receiver.returnValue();
        } catch(SQLException e) {
            throw queryException(sql, Optional.empty(), Optional.of(e));
        }
    }

    FluentJdbcException queryException(String sql, Optional<String> reason, Optional<SQLException> e) {
        String message = String.format("Error running query" + (reason.isPresent() ? ": " + reason.get() : "") + ", %s", sql);
        return e.isPresent() ? new FluentJdbcSqlException(message, e.get()) : new FluentJdbcException(message);
    }

    PreparedStatement preparedStatement(
            Connection con, 
            QuerySpecification querySpec
    ) throws SQLException {
        SqlAndParams sqlAndParams = querySpec.sqlAndParams(config);
        PreparedStatement statement = con.prepareStatement(sqlAndParams.sql());
        fetchSize(statement, querySpec.selectFetchSize);
        assignParams(statement, sqlAndParams.params());
        return statement;
    }

    void assignParams(PreparedStatement statement, List<Object> params) throws SQLException {
        config.paramAssigner.assignParams(statement, params);
    }

    private void fetchSize(PreparedStatement statement, Optional<Integer> selectFetchSize) throws SQLException {
        Optional<Integer> activeFetchSize = config.fetchSize(selectFetchSize);
        if(activeFetchSize.isPresent()) {
            statement.setFetchSize(activeFetchSize.get());
        }
    }
}
