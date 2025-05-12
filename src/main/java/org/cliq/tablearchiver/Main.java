package org.cliq.tablearchiver;

import static org.cliq.tablearchiver.DataSourceConfig.printTablePartitioning;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;


public class Main
{
	public static void main(String[] args) throws SQLException
	{
		String configFilePath = "src/main/resources/config.properties";
		String sqlScriptPath = "src/main/resources/schema_and_dummy_data_script.sql";

		Properties properties = DataSourceConfig.loadConfig(configFilePath);
		DataSource myDataSource = DataSourceConfig.createMySQLDataSource(properties, sqlScriptPath);
		List<DataSourceConfig.PartitionDetails> partitionDetails =printTablePartitioning(myDataSource.getConnection(), properties.getProperty("db.name"), "messages");
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		System.out.print("Enter an Partition ID for archive: \n\n");
		int number = 0;
		try {
			String input = reader.readLine();
			 number = Integer.parseInt(input);
			if(number >= partitionDetails.size())
			{
				throw new RuntimeException("Invalid partition ID. Please enter a valid number.");
			}
			System.out.println("You selected: " + number);
		} catch (IOException e) {
			System.out.println("Error reading input.");
		} catch (NumberFormatException e) {
			System.out.println("Invalid integer input.");
		}

		if (myDataSource == null) {
			System.err.println("DataSource is not configured. Please provide a valid DataSource.");
			return;
		}

		// --- Configure the Archiver using the Builder ---
		ArchiverConfig config = ArchiverConfig.builder()
			.messagesTable("messages") // Set your messages table name
			.archivedMetadataTable("archived_message_partitions") // Set your metadata table name
			.archiveTableNameFunction(partitionName -> {
				if (partitionName != null && partitionName.startsWith("p")) {
					return "messages_archive_" + partitionName.substring(1).replace("_", "_").toUpperCase();
				}
				throw new IllegalArgumentException("Invalid partition name format: " + partitionName);
			})
			.build();
		// ----------------------------------------------


		String partitionToArchive = partitionDetails.get(number).getPartitionName(); // Replace with the partition you want to archive

		PartitionArchiver archiver = new PartitionArchiver(myDataSource, config);

		try {
			archiver.archivePartition(partitionToArchive);
		} catch (SQLException e) {
			System.err.println("Failed to archive partition: " + e.getMessage());
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			System.err.println("Configuration error: " + e.getMessage());
			e.printStackTrace();
		}
	}

}