package org.molgenis.coding.backup;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import org.molgenis.coding.util.RecodeResponse;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityMetaData;
import org.molgenis.data.Repository;
import org.molgenis.data.support.DefaultAttributeMetaData;
import org.molgenis.data.support.DefaultEntityMetaData;
import org.molgenis.data.support.MapEntity;

public class BackupRepository implements Repository
{

	private final Collection<RecodeResponse> values;
	private final String documentType;
	private final boolean isMapped;
	private DefaultEntityMetaData defaultEntityMetaData = null;

	public BackupRepository(Collection<RecodeResponse> values, String documentType, boolean isMapped)
	{
		this.values = values;
		this.documentType = documentType;
		this.isMapped = isMapped;
	}

	@Override
	public void close() throws IOException
	{

	}

	@Override
	public Iterator<Entity> iterator()
	{
		return new Iterator<Entity>()
		{
			private final Iterator<RecodeResponse> iterator = values.iterator();

			@Override
			public boolean hasNext()
			{
				return iterator.hasNext();
			}

			@Override
			public Entity next()
			{
				RecodeResponse recodeResponse = iterator.next();
				Entity entity = new MapEntity();
				entity.set(BackupCodesInState.QUERYSTRING_FIELD, recodeResponse.getQueryString());
				entity.set(BackupCodesInState.IDENTIFIERS_FIELD, recodeResponse.getIdentifiers());
				entity.set(BackupCodesInState.DOCUMENT_ID_FIELD, recodeResponse.getHit().getDocumentId());
				entity.set(BackupCodesInState.SCORE_FIELD, recodeResponse.getHit().getScore());
				entity.set(BackupCodesInState.DOCUMENT_DATA_FIELD, recodeResponse.getHit().getColumnValueMap());
				entity.set(BackupCodesInState.IS_CUSTOM_SEARCHED_FIELD, recodeResponse.isCustomSearched());
				entity.set(BackupCodesInState.IS_FINALIZED_FIELD, recodeResponse.isFinalSelection());
				entity.set(BackupCodesInState.ADDED_DATE_FIELD, recodeResponse.getAddedDate().getTime());
				entity.set(BackupCodesInState.ADDED_DATE_STRING_FIELD, recodeResponse.getDateString());
				entity.set(BackupCodesInState.IS_MAPPED_FIELD, isMapped);
				return entity;
			}

			@Override
			public void remove()
			{

			}
		};
	}

	@Override
	public <E extends Entity> Iterable<E> iterator(Class<E> clazz)
	{
		return null;
	}

	@Override
	public String getUrl()
	{
		return null;
	}

	@Override
	public String getName()
	{
		return documentType;
	}

	@Override
	public EntityMetaData getEntityMetaData()
	{
		if (defaultEntityMetaData == null)
		{
			DefaultEntityMetaData defaultEntityMetaData = new DefaultEntityMetaData(documentType);
			defaultEntityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(
					BackupCodesInState.QUERYSTRING_FIELD));
			defaultEntityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(
					BackupCodesInState.IDENTIFIERS_FIELD));
			defaultEntityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(
					BackupCodesInState.DOCUMENT_ID_FIELD));
			defaultEntityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(BackupCodesInState.SCORE_FIELD));

			defaultEntityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(
					BackupCodesInState.DOCUMENT_DATA_FIELD));
			defaultEntityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(
					BackupCodesInState.IS_CUSTOM_SEARCHED_FIELD));

			defaultEntityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(
					BackupCodesInState.IS_FINALIZED_FIELD));
			defaultEntityMetaData
					.addAttributeMetaData(new DefaultAttributeMetaData(BackupCodesInState.ADDED_DATE_FIELD));

			defaultEntityMetaData.addAttributeMetaData(new DefaultAttributeMetaData(
					BackupCodesInState.ADDED_DATE_STRING_FIELD));
			defaultEntityMetaData
					.addAttributeMetaData(new DefaultAttributeMetaData(BackupCodesInState.IS_MAPPED_FIELD));
		}
		return defaultEntityMetaData;
	}
}
