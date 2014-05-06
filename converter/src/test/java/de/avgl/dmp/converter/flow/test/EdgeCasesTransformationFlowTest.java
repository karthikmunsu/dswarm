package de.avgl.dmp.converter.flow.test;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Provider;

import de.avgl.dmp.converter.GuicedTest;
import de.avgl.dmp.converter.flow.TransformationFlow;
import de.avgl.dmp.converter.morph.MorphScriptBuilder;
import de.avgl.dmp.persistence.model.job.Task;
import de.avgl.dmp.persistence.service.InternalModelServiceFactory;
import de.avgl.dmp.persistence.util.DMPPersistenceUtil;

public class EdgeCasesTransformationFlowTest extends GuicedTest {

	@Ignore
	@Test
	public void testWorkshop1EdgeCase1() throws Exception {

		final String expected = DMPPersistenceUtil.getResourceAsString("test-mabxml.ws1ec1.result.json");

		final Provider<InternalModelServiceFactory> internalModelServiceFactoryProvider = GuicedTest.injector
				.getProvider(InternalModelServiceFactory.class);

		final TransformationFlow flow = TransformationFlow.fromFile("ws1ec1morph.xml", internalModelServiceFactoryProvider);

		final String actual = flow.applyResource("ralfs_mabxml.tuples.json");

		final ArrayNode expectedJson = replaceKeyWithActualKey(expected, actual);
		final String finalExpected = DMPPersistenceUtil.getJSONObjectMapper().writeValueAsString(expectedJson);

		Assert.assertEquals(finalExpected, actual);
	}

	private ArrayNode replaceKeyWithActualKey(final String expected, final String actual) throws JsonParseException, JsonMappingException,
			IOException {

		// replace key with actual key
		final ArrayNode expectedJson = DMPPersistenceUtil.getJSONObjectMapper().readValue(expected, ArrayNode.class);
		final ObjectNode expectedTuple = (ObjectNode) expectedJson.get(0);
		final String expectedFieldName = expectedTuple.fieldNames().next();
		final ArrayNode expectedContent = (ArrayNode) expectedTuple.get(expectedFieldName);
		ObjectNode expectedContentJson = null;
		JsonNode typeNode = null;

		for (final JsonNode expectedContentJsonCandidate : expectedContent) {

			final String expectedContentJsonFieldName = expectedContentJsonCandidate.fieldNames().next();

			if (expectedContentJsonFieldName.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) {

				typeNode = expectedContentJsonCandidate;

				continue;
			}

			expectedContentJson = (ObjectNode) expectedContentJsonCandidate;
		}

		Assert.assertNotNull("expected content JSON shouldn't be null", expectedContentJson);

		final JsonNode expectedContentValue = expectedContentJson.get(expectedContentJson.fieldNames().next());

		Assert.assertNotNull("the actual transformation result shouldn't be null", actual);
		final ArrayNode actualJson = DMPPersistenceUtil.getJSONObjectMapper().readValue(actual, ArrayNode.class);
		Assert.assertNotNull("the deserialised JSON array of the actual transformation result shouldn't be null", actualJson);
		final ObjectNode actualTuple = (ObjectNode) actualJson.get(0);
		Assert.assertNotNull("the tuple of the deserialised JSON array of the actual transformation result shouldn't be null", actualTuple);
		final JsonNode actualContent = actualTuple.get(actualTuple.fieldNames().next());
		Assert.assertNotNull("the content of the tuple of the deserialised JSON array of the actual transformation result shouldn't be null",
				actualContent);
		Assert.assertTrue("the actual content should be a JSON array", actualContent.isArray());
		JsonNode actualContentJson = null;
		Integer typeNodePosition = null;
		int i = 0;

		for (final JsonNode actualContentJsonCandidate : actualContent) {

			i++;

			final String actualContentJsonFieldName = actualContentJsonCandidate.fieldNames().next();

			if (actualContentJsonFieldName.equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")) {

				typeNodePosition = i;

				continue;
			}

			actualContentJson = actualContentJsonCandidate;
		}

		Assert.assertNotNull("the acutal content JSON shouldn't be null", actualContentJson);
		Assert.assertTrue("the actual content JSON should be a JSON object", actualContentJson.isObject());

		final ObjectNode newExpectedContentJson = DMPPersistenceUtil.getJSONObjectMapper().createObjectNode();
		newExpectedContentJson.put(actualContentJson.fieldNames().next(), expectedContentValue);
		final ArrayNode newExpectedContent = DMPPersistenceUtil.getJSONObjectMapper().createArrayNode();

		if (typeNode != null) {

			if (typeNodePosition != null) {

				if (typeNodePosition == 1) {

					newExpectedContent.add(typeNode);
					newExpectedContent.add(newExpectedContentJson);
				} else {

					newExpectedContent.add(newExpectedContentJson);
					newExpectedContent.add(typeNode);
				}
			} else {

				newExpectedContent.add(newExpectedContentJson);
			}
		} else {

			newExpectedContent.add(newExpectedContentJson);
		}
		expectedTuple.put(expectedFieldName, newExpectedContent);

		return expectedJson;
	}
}