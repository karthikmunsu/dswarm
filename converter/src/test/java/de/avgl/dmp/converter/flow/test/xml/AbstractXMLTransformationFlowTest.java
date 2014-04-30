package de.avgl.dmp.converter.flow.test.xml;

import static org.junit.Assert.assertEquals;

import java.util.*;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.base.Optional;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Maps;
import com.google.inject.Provider;

import de.avgl.dmp.converter.GuicedTest;
import de.avgl.dmp.converter.flow.TransformationFlow;
import de.avgl.dmp.converter.flow.XMLSourceResourceGDMStmtsFlow;
import de.avgl.dmp.persistence.model.internal.Model;
import de.avgl.dmp.persistence.model.internal.gdm.GDMModel;
import de.avgl.dmp.persistence.model.job.Task;
import de.avgl.dmp.persistence.model.resource.Configuration;
import de.avgl.dmp.persistence.model.resource.DataModel;
import de.avgl.dmp.persistence.model.resource.Resource;
import de.avgl.dmp.persistence.model.resource.ResourceType;
import de.avgl.dmp.persistence.model.resource.utils.ConfigurationStatics;
import de.avgl.dmp.persistence.model.schema.Attribute;
import de.avgl.dmp.persistence.model.schema.AttributePath;
import de.avgl.dmp.persistence.model.schema.Clasz;
import de.avgl.dmp.persistence.model.schema.Schema;
import de.avgl.dmp.persistence.model.types.Tuple;
import de.avgl.dmp.persistence.service.InternalModelServiceFactory;
import de.avgl.dmp.persistence.service.internal.graph.InternalGDMGraphService;
import de.avgl.dmp.persistence.service.internal.test.utils.InternalGDMGraphServiceTestUtils;
import de.avgl.dmp.persistence.service.resource.ConfigurationService;
import de.avgl.dmp.persistence.service.resource.DataModelService;
import de.avgl.dmp.persistence.service.resource.ResourceService;
import de.avgl.dmp.persistence.service.schema.ClaszService;
import de.avgl.dmp.persistence.service.schema.SchemaService;
import de.avgl.dmp.persistence.service.schema.test.utils.AttributePathServiceTestUtils;
import de.avgl.dmp.persistence.service.schema.test.utils.AttributeServiceTestUtils;
import de.avgl.dmp.persistence.service.schema.test.utils.SchemaServiceTestUtils;
import de.avgl.dmp.persistence.util.DMPPersistenceUtil;

public abstract class AbstractXMLTransformationFlowTest extends GuicedTest {

	protected final String						taskJSONFileName;

	protected final String						expectedResultJSONFileName;

	protected final String						recordTag;

	protected final String						xmlNamespace;

	protected final String						exampleDataResourceFileName;

	protected final ObjectMapper				objectMapper;

	private final AttributeServiceTestUtils		attributeServiceTestUtils;
	private final AttributePathServiceTestUtils	attributePathServiceTestUtils;
	private final SchemaServiceTestUtils	schemaServiceTestUtils;

	public AbstractXMLTransformationFlowTest(final String taskJSONFileNameArg, final String expectedResultJSONFileNameArg, final String recordTagArg,
			final String xmlNamespaceArg, final String exampleDataResourceFileNameArg) {

		objectMapper = injector.getInstance(ObjectMapper.class);

		taskJSONFileName = taskJSONFileNameArg;
		expectedResultJSONFileName = expectedResultJSONFileNameArg;
		recordTag = recordTagArg;
		xmlNamespace = xmlNamespaceArg;
		exampleDataResourceFileName = exampleDataResourceFileNameArg;

		attributeServiceTestUtils = new AttributeServiceTestUtils();
		attributePathServiceTestUtils = new AttributePathServiceTestUtils();
		schemaServiceTestUtils = new SchemaServiceTestUtils();
	}

	@Test
	public void testXMLDataResourceEndToEnd() throws Exception {

		final String taskJSONString = DMPPersistenceUtil.getResourceAsString(taskJSONFileName);
		final String expected = DMPPersistenceUtil.getResourceAsString(expectedResultJSONFileName);

		// process input data model
		final ConfigurationService configurationService = injector.getInstance(ConfigurationService.class);
		final Configuration configuration = configurationService.createObjectTransactional().getObject();

		configuration.addParameter(ConfigurationStatics.RECORD_TAG, new TextNode(recordTag));

		if (xmlNamespace != null) {

			configuration.addParameter(ConfigurationStatics.XML_NAMESPACE, new TextNode(xmlNamespace));
		}

		configuration.addParameter(ConfigurationStatics.STORAGE_TYPE, new TextNode("xml"));

		Configuration updatedConfiguration = configurationService.updateObjectTransactional(configuration).getObject();

		final ResourceService resourceService = injector.getInstance(ResourceService.class);
		final Resource resource = resourceService.createObjectTransactional().getObject();
		resource.setName(exampleDataResourceFileName);
		resource.setType(ResourceType.FILE);
		resource.addConfiguration(updatedConfiguration);

		Resource updatedResource = resourceService.updateObjectTransactional(resource).getObject();

		final DataModelService dataModelService = injector.getInstance(DataModelService.class);
		final DataModel inputDataModel = dataModelService.createObjectTransactional().getObject();

		inputDataModel.setDataResource(updatedResource);
		inputDataModel.setConfiguration(updatedConfiguration);

		DataModel updatedInputDataModel = dataModelService.updateObjectTransactional(inputDataModel).getObject();

		final XMLSourceResourceGDMStmtsFlow flow2 = new XMLSourceResourceGDMStmtsFlow(updatedInputDataModel);

		final List<GDMModel> gdmModels = flow2.applyResource(exampleDataResourceFileName);

		Assert.assertNotNull("GDM model list shouldn't be null", gdmModels);
		Assert.assertFalse("GDM model list shouldn't be empty", gdmModels.isEmpty());

		// write RDF models at once
		final de.avgl.dmp.graph.json.Model model = new de.avgl.dmp.graph.json.Model();
		String recordClassUri = null;

		for (final GDMModel gdmModel : gdmModels) {

			Assert.assertNotNull("the GDM statements of the GDM model shouldn't be null", gdmModel.getModel());

			final de.avgl.dmp.graph.json.Model aModel = gdmModel.getModel();

			Assert.assertNotNull("the resources of the GDM model shouldn't be null", aModel.getResources());

			final Collection<de.avgl.dmp.graph.json.Resource> resources = aModel.getResources();

			for (final de.avgl.dmp.graph.json.Resource aResource : resources) {

				model.addResource(aResource);

				if (recordClassUri == null) {

					recordClassUri = gdmModel.getRecordClassURI();
				}

			}
		}

		final GDMModel gdmModel = new GDMModel(model, null, recordClassUri);

		// System.out.println(objectMapper.writeValueAsString(rdfModel.getSchema()));
		//
		// for(final AttributePathHelper attributePathHelper : rdfModel.getAttributePaths()) {
		//
		// System.out.println(attributePathHelper.toString());
		// }
		//
		// System.out.println(objectMapper.writeValueAsString(rdfModel.toJSON()));

		// write model and retrieve tuples
		final InternalGDMGraphService gdmService = injector.getInstance(InternalGDMGraphService.class);
		gdmService.createObject(updatedInputDataModel.getId(), gdmModel);

		final Optional<Map<String, Model>> optionalModelMap = gdmService.getObjects(updatedInputDataModel.getId(), Optional.of(1));

		final Iterator<Tuple<String, JsonNode>> tuples = dataIterator(optionalModelMap.get().entrySet().iterator());

		final String inputDataModelJSONString = objectMapper.writeValueAsString(updatedInputDataModel);
		final ObjectNode inputDataModelJSON = objectMapper.readValue(inputDataModelJSONString, ObjectNode.class);

		// manipulate input data model
		final ObjectNode taskJSON = objectMapper.readValue(taskJSONString, ObjectNode.class);
		taskJSON.put("input_data_model", inputDataModelJSON);

		// manipulate output data model (output data model = internal model (for now))
		final long internalModelId = 1;
		final DataModel outputDataModel = dataModelService.getObject(internalModelId);
		final String outputDataModelJSONString = objectMapper.writeValueAsString(outputDataModel);
		final ObjectNode outputDataModelJSON = objectMapper.readValue(outputDataModelJSONString, ObjectNode.class);
		taskJSON.put("output_data_model", outputDataModelJSON);

		final String finalTaskJSONString = objectMapper.writeValueAsString(taskJSON);

		final Task task = objectMapper.readValue(finalTaskJSONString, Task.class);

		final Provider<InternalModelServiceFactory> internalModelServiceFactoryProvider = injector.getProvider(InternalModelServiceFactory.class);

		final TransformationFlow flow = TransformationFlow.fromTask(task, internalModelServiceFactoryProvider);

		flow.getScript();

		final String actual = flow.apply(tuples, true);

		compareResults(expected, actual);

		// retrieve updated fresh data model
		final DataModel freshInputDataModel = dataModelService.getObject(updatedInputDataModel.getId());

		Assert.assertNotNull("the fresh data model shouldn't be null", freshInputDataModel);
		Assert.assertNotNull("the schema of the fresh data model shouldn't be null", freshInputDataModel.getSchema());

		final Schema schema = freshInputDataModel.getSchema();

		// System.out.println(objectMapper.writeValueAsString(schema));

		Assert.assertNotNull("the record class of the schema of the fresh data model shouldn't be null", schema.getRecordClass());

		final Clasz recordClass = schema.getRecordClass();

		// clean-up
		// TODO: move clean-up to @After

		final DataModel freshOutputDataModel = dataModelService.getObject(internalModelId);

		final Schema outputDataModelSchema = freshOutputDataModel.getSchema();

		final Map<Long, Attribute> attributes = Maps.newHashMap();

		final Map<Long, AttributePath> attributePaths = Maps.newLinkedHashMap();

		if (schema != null) {

			final Set<AttributePath> attributePathsToDelete = schema.getAttributePaths();

			if (attributePathsToDelete != null) {

				for (final AttributePath attributePath : attributePathsToDelete) {

					attributePaths.put(attributePath.getId(), attributePath);

					final Set<Attribute> attributesToDelete = attributePath.getAttributes();

					if (attributesToDelete != null) {

						for (final Attribute attribute : attributesToDelete) {

							attributes.put(attribute.getId(), attribute);
						}
					}
				}
			}
		}

		final SchemaService schemaService = injector.getInstance(SchemaService.class);

		schemaServiceTestUtils.removeAddedAttributePathsFromOutputModelSchema(outputDataModelSchema, attributes, attributePaths);

		dataModelService.deleteObject(updatedInputDataModel.getId());

		schemaService.deleteObject(schema.getId());

		for (final AttributePath attributePath : attributePaths.values()) {

			attributePathServiceTestUtils.deleteObject(attributePath);
		}

		for (final Attribute attribute : attributes.values()) {

			attributeServiceTestUtils.deleteObject(attribute);
		}

		final ClaszService claszService = injector.getInstance(ClaszService.class);

		claszService.deleteObject(recordClass.getId());

		configurationService.deleteObject(updatedConfiguration.getId());
		resourceService.deleteObject(updatedResource.getId());

		// clean-up graph db
		InternalGDMGraphServiceTestUtils.cleanGraphDB();
	}

	protected void compareResults(final String expectedResultJSONString, final String actualResultJSONString) throws Exception {

		final ArrayNode expectedJSONArray = objectMapper.readValue(expectedResultJSONString, ArrayNode.class);
		final ObjectNode expectedElementInArray = (ObjectNode) expectedJSONArray.get(0);
		final String expectedKeyInArray = expectedElementInArray.fieldNames().next();
		final ObjectNode expectedJSON = (ObjectNode) expectedElementInArray.get(expectedKeyInArray).get(0);
		final String finalExpectedJSONString = objectMapper.writeValueAsString(expectedJSON);

		final ArrayNode actualJSONArray = objectMapper.readValue(actualResultJSONString, ArrayNode.class);
		final ObjectNode actualElementInArray = (ObjectNode) actualJSONArray.get(0);
		final String actualKeyInArray = actualElementInArray.fieldNames().next();
		final ArrayNode actualKeyArray = (ArrayNode) actualElementInArray.get(actualKeyInArray);
		ObjectNode actualJSON = null;

		for(final JsonNode actualKeyArrayItem : actualKeyArray) {

			if(actualKeyArrayItem.get("http://www.w3.org/1999/02/22-rdf-syntax-ns#type") != null) {

				// don't take the type JSON object for comparison

				continue;
			}

			actualJSON = (ObjectNode) actualKeyArrayItem;
		}

		final String finalActualJSONString = objectMapper.writeValueAsString(actualJSON);

		assertEquals(finalExpectedJSONString.length(), finalActualJSONString.length());
	}

	private Iterator<Tuple<String, JsonNode>> dataIterator(final Iterator<Map.Entry<String, Model>> triples) {
		return new AbstractIterator<Tuple<String, JsonNode>>() {

			@Override
			protected Tuple<String, JsonNode> computeNext() {
				if (triples.hasNext()) {
					final Map.Entry<String, Model> nextTriple = triples.next();
					final String recordId = nextTriple.getKey();
					final JsonNode jsonNode = nextTriple.getValue().toRawJSON();
					return Tuple.tuple(recordId, jsonNode);
				}
				return endOfData();
			}
		};
	}
}