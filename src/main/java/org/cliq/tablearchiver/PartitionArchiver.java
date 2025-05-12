package org.cliq.tablearchiver;

import static org.cliq.tablearchiver.DataSourceConfig.printTablePartitioning;

import javax.sql.DataSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;

public class PartitionArchiver {

	private final DataSource dataSource;
	private final ArchiverConfig config;

	public PartitionArchiver(DataSource dataSource, ArchiverConfig config)
	{
		this.dataSource = dataSource;
		this.config = config;// Default if no function provided
	}

	// A default naming function if none is provided in the config
	private String deriveArchiveTableNameDefault(String partitionName) {
		// Example: p2023_q1 -> messages_archive_2023_Q1
		if (partitionName != null && partitionName.startsWith("p")) {
			return config.getMessagesTable() + "_archive_" + partitionName.substring(1).replace("_", "_").toUpperCase();
		}
		// Default or error case
		return config.getMessagesTable() + "_archive_" + partitionName;
	}


	/**
	 * Archives a specific partition from the messages table.
	 *
	 * @param partitionName The name of the partition to archive (e.g., "p2025_q1").
	 * @throws SQLException If a database error occurs.
	 */
	public void archivePartition(String partitionName) throws SQLException {
		String archiveTableName = config.getArchiveTableNameSupplier().apply(partitionName);
		Connection conn = null;

		try {
			conn = dataSource.getConnection();
			// manage transactions manually
			conn.setAutoCommit(false);

			System.out.println("Starting archival process for partition: " + partitionName);

			//0. Check if the partition exists before proceeding
			if (!partitionExists(conn, partitionName)) {
				throw new SQLException("Unable to archive: Partition '" + partitionName + "' does not exist in table '" + config.getMessagesTable() + "'.");
			}


			// 1. Check and Create Archive Table if Not Exists

			if (!archiveTableExists(conn, archiveTableName))
			{
				System.out.println("Archive table '" + archiveTableName + "' does not exist. Creating...");
				createArchiveTable(conn, archiveTableName);
				System.out.println("Archive table '" + archiveTableName + "' created.");
			} else
			{
				System.out.println("Archive table '" + archiveTableName + "' already exists.");
			}

			// 2. Perform Partition Exchange
			System.out.println("Exchanging partition '" + partitionName + "' with table '" + archiveTableName + "'...");
			exchangePartition(conn, partitionName, archiveTableName);
			System.out.println("Partition exchange complete.");

			// 3. Get Metadata from Archived Table
			System.out.println("Fetching metadata from archived table '" + archiveTableName + "'...");
			ArchivedPartitionMetadata metadata = getArchivedPartitionMetadata(conn, archiveTableName);
			System.out.println("Metadata fetched: " + metadata);

			// 4. Insert Metadata into archived_message_partitions
			System.out.println("Inserting metadata into '" + config.getArchivedMetadataTable() + "'...");
			insertArchivedMetadata(conn, archiveTableName, metadata);
			System.out.println("Metadata inserted.");

			// 5. Drop Original Partition
			System.out.println("Dropping original partition '" + partitionName + "' from '" + config.getMessagesTable() + "'...");
			dropPartition(conn, partitionName);
			System.out.println("Original partition dropped.");
			conn.commit();
			System.out.println("Archival process for partition '" + partitionName + "' completed successfully.");

		} catch (SQLException e) {
			if (conn != null) {
				try {
					conn.rollback();
					System.err.println("Transaction rolled back due to error.");
				} catch (SQLException rollbackErr) {
					System.err.println("Error during rollback: " + rollbackErr.getMessage());
				}
			}
			System.err.println("Archival process failed for partition '" + partitionName + "'.");
			throw e;
		} finally {
			if (conn != null) {
				try {
					conn.setAutoCommit(true);
					conn.close();
				} catch (SQLException closeErr) {
					System.err.println("Error closing connection: " + closeErr.getMessage());
				}
			}
		}
	}

	private boolean partitionExists(Connection conn, String partitionName) throws SQLException {
		String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.PARTITIONS " +
			"WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND PARTITION_NAME = ?";
		try (PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, config.getMessagesTable());
			stmt.setString(2, partitionName);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getInt(1) > 0;
				}
				return false;
			}
		}
	}


	/**
	 * Checks if an archive table with the given name exists.
	 *
	 * @param conn The database connection.
	 * @param tableName The name of the table to check.
	 * @return True if the table exists, false otherwise.
	 * @throws SQLException If a database error occurs.
	 */
	private boolean archiveTableExists(Connection conn, String tableName) throws SQLException {
		DatabaseMetaData dbm = conn.getMetaData();
		try (ResultSet tables = dbm.getTables(null, null, tableName, new String[]{"TABLE"})) {
			return tables.next();
		}
	}

	/**
	 * Creates the archive table based on the structure of the messages table.
	 *
	 * @param conn The database connection.
	 * @param archiveTableName The name of the archive table to create.
	 * @throws SQLException If a database error occurs.
	 */
	private void createArchiveTable(Connection conn, String archiveTableName) throws SQLException {
		String createTableSql = "CREATE TABLE " + archiveTableName + " LIKE " + config.getMessagesTable();
		String alterTableSql = "ALTER TABLE " + archiveTableName + " REMOVE PARTITIONING";
		// Optional: ALTER TABLE " + archiveTableName + " AUTO_INCREMENT = 10000; // If you want to reset AI

		try (Statement stmt = conn.createStatement()) {
			stmt.execute(createTableSql);
			stmt.execute(alterTableSql);
		}
	}

	/**
	 * Executes the ALTER TABLE ... EXCHANGE PARTITION statement.
	 *
	 * @param conn The database connection.
	 * @param partitionName The name of the partition in the messages table.
	 * @param archiveTableName The name of the target archive table.
	 * @throws SQLException If a database error occurs.
	 */
	private void exchangePartition(Connection conn, String partitionName, String archiveTableName) throws SQLException {
		String sql = "ALTER TABLE " + config.getMessagesTable() +
			" EXCHANGE PARTITION " + partitionName +
			" WITH TABLE " + archiveTableName;
		try (Statement stmt = conn.createStatement()) {
			stmt.execute(sql);
		}
	}

	/**
	 * Queries the archived table to get the min/max message ID and timestamps.
	 *
	 * @param conn The database connection.
	 * @param archiveTableName The name of the archived table.
	 * @return An ArchivedPartitionMetadata object containing the details.
	 * @throws SQLException If a database error occurs or no data is found.
	 */
	private ArchivedPartitionMetadata getArchivedPartitionMetadata(Connection conn, String archiveTableName) throws SQLException {
		// Assuming the archive table has min_message_id, max_message_id, created_at columns
		String sql = "SELECT MIN(message_id), MAX(message_id), MIN(created_at), MAX(created_at) FROM " + archiveTableName;
		try (Statement stmt = conn.createStatement();
			 ResultSet rs = stmt.executeQuery(sql)) {

			if (rs.next()) {
				long minMessageId = rs.getLong(1);
				long maxMessageId = rs.getLong(2);
				Timestamp minTimestamp = rs.getTimestamp(3);
				Timestamp maxTimestamp = rs.getTimestamp(4);

				// Check if the table was empty after exchange (shouldn't happen with EXCHANGE, but good practice)
				if (rs.wasNull() || minTimestamp == null || maxTimestamp == null) {
					throw new SQLException("Archived table '" + archiveTableName + "' appears to be empty or metadata is null.");
				}

				// Convert Timestamp to LocalDateTime
				LocalDateTime startTimestamp = minTimestamp.toLocalDateTime();
				LocalDateTime endTimestamp = maxTimestamp.toLocalDateTime();


				return new ArchivedPartitionMetadata(
					archiveTableName,
					startTimestamp,
					endTimestamp,
					minMessageId,
					maxMessageId,
					LocalDateTime.now() // Timestamp of when archival metadata is recorded
				);
			} else {
				// This case should ideally not be reached if the query runs without error
				throw new SQLException("Could not retrieve metadata from archived table '" + archiveTableName + "'.");
			}
		}
	}

	/**
	 * Inserts the metadata of the archived partition into the metadata table.
	 *
	 * @param conn The database connection.
	 * @param archiveTableName The name of the archive table.
	 * @param metadata The metadata object.
	 * @throws SQLException If a database error occurs.
	 */
	private void insertArchivedMetadata(Connection conn, String archiveTableName, ArchivedPartitionMetadata metadata) throws SQLException {
		String sql = "INSERT INTO " + config.getArchivedMetadataTable() +
			" (archive_table_name, start_timestamp, end_timestamp, min_message_id, max_message_id, archived_at) " +
			" VALUES (?, ?, ?, ?, ?, ?)";

		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, archiveTableName);
			pstmt.setTimestamp(2, Timestamp.valueOf(metadata.getStartTimestamp()));
			pstmt.setTimestamp(3, Timestamp.valueOf(metadata.getEndTimestamp()));
			pstmt.setLong(4, metadata.getMinMessageId());
			pstmt.setLong(5, metadata.getMaxMessageId());
			pstmt.setTimestamp(6, Timestamp.valueOf(metadata.getArchivedAt()));

			pstmt.executeUpdate();
		}
	}

	/**
	 * Drops the original partition from the messages table.
	 *
	 * @param conn The database connection.
	 * @param partitionName The name of the partition to drop.
	 * @throws SQLException If a database error occurs.
	 */
	private void dropPartition(Connection conn, String partitionName) throws SQLException {
		String sql = "ALTER TABLE " + config.getMessagesTable() + " DROP PARTITION " + partitionName;
		try (Statement stmt = conn.createStatement()) {
			stmt.execute(sql);
		}
	}


	// Helper class to hold archived partition metadata
	// This class is the same as before, no changes needed based on config
	private static class ArchivedPartitionMetadata {
		private final String archiveTableName;
		private final LocalDateTime startTimestamp;
		private final LocalDateTime endTimestamp;
		private final long minMessageId;
		private final long maxMessageId;
		private final LocalDateTime archivedAt;

		public ArchivedPartitionMetadata(String archiveTableName, LocalDateTime startTimestamp, LocalDateTime endTimestamp, long minMessageId, long maxMessageId, LocalDateTime archivedAt) {
			this.archiveTableName = archiveTableName;
			this.startTimestamp = startTimestamp;
			this.endTimestamp = endTimestamp;
			this.minMessageId = minMessageId;
			this.maxMessageId = maxMessageId;
			this.archivedAt = archivedAt;
		}

		public String getArchiveTableName() { return archiveTableName; }
		public LocalDateTime getStartTimestamp() { return startTimestamp; }
		public LocalDateTime getEndTimestamp() { return endTimestamp; }
		public long getMinMessageId() { return minMessageId; }
		public long getMaxMessageId() { return maxMessageId; }
		public LocalDateTime getArchivedAt() { return archivedAt; }

		@Override
		public String toString() {
			return "ArchivedPartitionMetadata{" +
				"archiveTableName='" + archiveTableName + '\'' +
				", startTimestamp=" + startTimestamp +
				", endTimestamp=" + endTimestamp +
				", minMessageId=" + minMessageId +
				", maxMessageId=" + maxMessageId +
				", archivedAt=" + archivedAt +
				'}';
		}
	}

	// Example Usage (requires a DataSource implementation and ArchiverConfig)
//	public static void main(String[] args) throws SQLException
//	{
//		String configFilePath = "src/main/resources/config.properties";
//		String sqlScriptPath = "src/main/resources/schema_and_dummy_data_script.sql";
//
//		Properties properties = DataSourceConfig.loadConfig(configFilePath);
//		DataSource myDataSource = DataSourceConfig.createMySQLDataSource(properties, sqlScriptPath);
//		List<DataSourceConfig.PartitionDetails> partitionDetails =printTablePartitioning(myDataSource.getConnection(), properties.getProperty("db.name"), "messages");
//		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
//		System.out.print("Enter an Partition ID for archive: ");
//
//		try {
//			String input = reader.readLine();
//			int number = Integer.parseInt(input);
//			if(partitionDetails.size()<=number)
//				throw new SQLException("Invalid partition ID. Please enter a valid number.");
//			System.out.println("You selected: " + number);
//		} catch (IOException e) {
//			System.out.println("Error reading input.");
//		} catch (NumberFormatException e) {
//			System.out.println("Invalid integer input.");
//		}
//
//		if (myDataSource == null) {
//			System.err.println("DataSource is not configured. Please provide a valid DataSource.");
//			return;
//		}
//
//		// --- Configure the Archiver using the Builder ---
//		ArchiverConfig config = ArchiverConfig.builder()
//			.messagesTable("messages") // Set your messages table name
//			.archivedMetadataTable("archived_message_partitions") // Set your metadata table name
//			.archiveTableNameFunction(partitionName -> {
//				if (partitionName != null && partitionName.startsWith("p")) {
//					return "messages_archive_" + partitionName.substring(1).replace("_", "_").toUpperCase();
//				}
//				throw new IllegalArgumentException("Invalid partition name format: " + partitionName);
//			})
//			.build();
//		// ----------------------------------------------
//
//
//		String partitionToArchive = properties.getProperty("archiver.partition");
//
//		PartitionArchiver archiver = new PartitionArchiver(myDataSource, config);
//
//		try {
//			archiver.archivePartition(partitionToArchive);
//		} catch (SQLException e) {
//			System.err.println("Failed to archive partition: " + e.getMessage());
//			e.printStackTrace();
//		} catch (IllegalArgumentException e) {
//			System.err.println("Configuration error: " + e.getMessage());
//			e.printStackTrace();
//		}
//	}
}
