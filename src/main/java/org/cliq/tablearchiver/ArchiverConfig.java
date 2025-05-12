package org.cliq.tablearchiver;

import java.util.Objects;
import java.util.function.Function;

/**
 * Configuration for the PartitionArchiver tool.
 */
public class ArchiverConfig {

	private final String messagesTable;
	private final String archivedMetadataTable;
	private final Function<String,String> archiveTableNameSupplier;

	// Private constructor to enforce usage of the Builder
	private ArchiverConfig(Builder builder) {
		this.messagesTable = builder.messagesTable;
		this.archivedMetadataTable = builder.archivedMetadataTable;
		this.archiveTableNameSupplier = builder.archiveTableNameSupplier;
	}

	/**
	 * Get the name of the main messages table.
	 * @return The messages table name.
	 */
	public String getMessagesTable() {
		return messagesTable;
	}

	/**
	 * Get the name of the table storing archived partition metadata.
	 * @return The archived metadata table name.
	 */
	public String getArchivedMetadataTable() {
		return archivedMetadataTable;
	}

	/**
	 * Get the function for deriving archive table names based on partition name.
	 * @return The function that takes a partition name and returns an archive table name.
	 */
	public Function<String, String> getArchiveTableNameSupplier()
	{
		return archiveTableNameSupplier;
	}



	/**
	 * Static method to get a new instance of the Builder.
	 * @return A new Builder instance.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder class for ArchiverConfig.
	 */
	public static class Builder {
		private String messagesTable;
		private String archivedMetadataTable;
		private Function<String,String> archiveTableNameSupplier; // Consider Function<String, String>

		// Private constructor
		private Builder() {}

		/**
		 * Set the name of the main messages table.
		 * @param messagesTable The messages table name.
		 * @return The Builder instance.
		 */
		public Builder messagesTable(String messagesTable) {
			this.messagesTable = messagesTable;
			return this;
		}

		/**
		 * Set the name of the table storing archived partition metadata.
		 * @param archivedMetadataTable The archived metadata table name.
		 * @return The Builder instance.
		 */
		public Builder archivedMetadataTable(String archivedMetadataTable) {
			this.archivedMetadataTable = archivedMetadataTable;
			return this;
		}



		/**
		 * Set the function for deriving archive table names based on partition name.
		 * This is a more explicit and recommended approach than using a Supplier.
		 * @param archiveTableNameFunction The function that takes a partition name and returns an archive table name.
		 * @return The Builder instance.
		 */
		public Builder archiveTableNameFunction(Function<String, String> archiveTableNameFunction)
		{

			this.archiveTableNameSupplier = archiveTableNameFunction;
			return this;
		}


		/**
		 * Build the ArchiverConfig instance.
		 * @return The built ArchiverConfig.
		 * @throws NullPointerException if required fields are not set.
		 */
		public ArchiverConfig build() {
			Objects.requireNonNull(messagesTable, "messagesTable cannot be null");
			Objects.requireNonNull(archivedMetadataTable, "archivedMetadataTable cannot be null");
			Objects.requireNonNull(archiveTableNameSupplier, "archiveTableNameSupplier (or Function) cannot be null");
			return new ArchiverConfig(this);
		}
	}
}

