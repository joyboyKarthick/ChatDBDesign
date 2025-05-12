package org.cliq.tablearchiver;

import com.mysql.cj.jdbc.ConnectionImpl;
import com.mysql.cj.jdbc.MysqlDataSource;

import javax.sql.DataSource;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DataSourceConfig
{

	public static DataSource createMySQLDataSource(Properties properties, String sqlScriptPath)
	{

		// Initialize a dataSource without specifying the database
		MysqlDataSource dataSource = new MysqlDataSource();
		dataSource.setServerName(properties.getProperty("db.server"));
		dataSource.setPortNumber(Integer.parseInt(properties.getProperty("db.port", "3306")));
		dataSource.setUser(properties.getProperty("db.user"));
		dataSource.setPassword(properties.getProperty("db.password"));

		try(Connection conn = dataSource.getConnection())
		{

			dropDatabaseIfExists(conn, properties.getProperty("db.name"));
			// Check if the database exists; if not, create it
			createDatabaseIfNotExists(conn, properties.getProperty("db.name"));
			// Once the database is created or confirmed, switch to the target database
			dataSource.setDatabaseName(properties.getProperty("db.name"));
			((ConnectionImpl) conn).setDatabase(properties.getProperty("db.name"));
			// Execute the SQL script
			executeSqlScriptNew(conn, sqlScriptPath);
			System.out.println("Database connection and script execution successful!");
		}
		catch(SQLException e)
		{
			System.err.println("Failed to make connection or execute script!");
			e.printStackTrace();
		}

		return dataSource;
	}

	static Properties loadConfig(String configFilePath)
	{
		Properties properties = new Properties();
		try(FileInputStream inputStream = new FileInputStream(configFilePath))
		{
			properties.load(inputStream);
		}
		catch(IOException e)
		{
			System.err.println("Error loading configuration file: " + e.getMessage());
			e.printStackTrace();
			throw new RuntimeException("Unable to load configuration.");
		}
		return properties;
	}

	private static void dropDatabaseIfExists(Connection conn, String dbName) throws SQLException
	{

		String checkDbSql = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?";
		try(var stmt = conn.prepareStatement(checkDbSql))
		{
			stmt.setString(1, dbName);
			try(var rs = stmt.executeQuery())
			{
				if(rs.next())
				{
					String createDbSql = "DROP DATABASE " + dbName;
					try(var createStmt = conn.createStatement())
					{
						createStmt.execute(createDbSql);
						System.out.println("Database '" + dbName + "' Deleted.");
					}
				}
			}
		}
	}

	private static void createDatabaseIfNotExists(Connection conn, String dbName) throws SQLException
	{

		String checkDbSql = "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?";
		try(var stmt = conn.prepareStatement(checkDbSql))
		{
			stmt.setString(1, dbName);
			try(var rs = stmt.executeQuery())
			{
				if(!rs.next())
				{
					String createDbSql = "CREATE DATABASE IF NOT EXISTS " + dbName;
					try(var createStmt = conn.createStatement())
					{
						createStmt.execute(createDbSql);
						System.out.println("Database '" + dbName + "' created.");
					}
				}
			}
		}
	}

	private static void executeSqlScriptNew(Connection conn, String scriptPath)
	{
		try(BufferedReader reader = new BufferedReader(new FileReader(scriptPath)))
		{
			StringBuilder sqlStatement = new StringBuilder();
			String line;
			String currentDelimiter = ";";

			while((line = reader.readLine()) != null)
			{
				line = line.trim();

				if(line.isEmpty() || line.startsWith("--"))
					continue;

				// Handle DELIMITER directive (e.g., DELIMITER $$)
				if(line.toUpperCase().startsWith("DELIMITER "))
				{
					currentDelimiter = line.substring("DELIMITER ".length()).trim();
					continue;
				}

				sqlStatement.append(line).append("\n");

				// Check if the statement ends with the current delimiter
				if(sqlStatement.toString().trim().endsWith(currentDelimiter))
				{
					// Remove the delimiter from the end
					String sql = sqlStatement.toString().trim();
					sql = sql.substring(0, sql.length() - currentDelimiter.length()).trim();

					try(Statement stmt = conn.createStatement())
					{
						stmt.execute(sql);
					}
					catch(SQLException e)
					{
						System.err.println("Error executing SQL:\n" + sql);
						e.printStackTrace();
					}

					sqlStatement.setLength(0); // Reset for next block
				}
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public static List<PartitionDetails> printTablePartitioning(Connection conn, String database, String table)
	{
		List<PartitionDetails> partitionDetails = new ArrayList<>();
		String query = """
			SELECT PARTITION_NAME, TABLE_ROWS, PARTITION_EXPRESSION, PARTITION_DESCRIPTION
			FROM INFORMATION_SCHEMA.PARTITIONS
			WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?;
			""";

		try(PreparedStatement stmt = conn.prepareStatement(query))
		{
			stmt.setString(1, database);
			stmt.setString(2, table);

			try(ResultSet rs = stmt.executeQuery())
			{
				System.out.printf("Partitions for table `%s.%s`:\n", database, table);
				int i =0;
				while(rs.next())
				{
					String partition = rs.getString("PARTITION_NAME");
					String expression = rs.getString("PARTITION_EXPRESSION");
					String description = rs.getString("PARTITION_DESCRIPTION");
					long rows = rs.getLong("TABLE_ROWS");
					if(partition.equals("pmax"))
						continue;
					partitionDetails.add(new PartitionDetails(partition, rows, expression, description));
					System.out.printf("id :%s | Partition: %-20s | Rows: %-10d | Expr: %-30s | Desc: %s\n",i,
						partition, rows, expression, description);
					i=i+1;
				}
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		return partitionDetails;
	}

	public static class PartitionDetails
	{
		private String partitionName;
		private long rows;
		private String expression;
		private String description;

		public PartitionDetails(String partitionName, long rows, String expression, String description)
		{
			this.partitionName = partitionName;
			this.rows = rows;
			this.expression = expression;
			this.description = description;
		}

		public String getPartitionName()
		{
			return partitionName;
		}

		public long getRows()
		{
			return rows;
		}

		public String getExpression()
		{
			return expression;
		}

		public String getDescription()
		{
			return description;
		}
	}



	public static void main(String[] args) throws SQLException
	{
		// Example usage: Replace with the path to your config file and SQL script
		String configFilePath = "src/main/resources/config.properties";
		String sqlScriptPath = "src/main/resources/schema_and_dummy_data_script.sql";
		Properties properties = loadConfig(configFilePath);
		DataSource dataSource = createMySQLDataSource(properties, sqlScriptPath);
		printTablePartitioning(dataSource.getConnection(), properties.getProperty("db.name"), "messages");

		// Additional logic to use the dataSource can be added here
	}
}
