package de.avgl.dmp.converter.flow.test;

import static org.junit.Assert.assertEquals;

import java.io.StringWriter;

import org.culturegraph.mf.stream.converter.JsonEncoder;
import org.culturegraph.mf.stream.sink.ObjectJavaIoWriter;
import org.culturegraph.mf.stream.source.StringReader;
import org.junit.Assert;
import org.junit.Test;

import de.avgl.dmp.converter.flow.TransformationFlow;
import de.avgl.dmp.converter.mf.stream.reader.CsvReader;
import de.avgl.dmp.init.util.DMPUtil;
import de.avgl.dmp.persistence.mapping.JsonToPojoMapper;
import de.avgl.dmp.persistence.model.job.Job;

public class TransformationFlowTest {

	@Test
	public void testEndToEnd() throws Exception {

		final String request = DMPUtil.getResourceAsString("complex-request.json");
		final String expected = DMPUtil.getResourceAsString("complex-result.json");

		final Job job = new JsonToPojoMapper().toJob(request);
		final TransformationFlow flow = TransformationFlow.fromJob(job);

		final String actual = flow.apply();

		assertEquals(expected, actual);
	}

	@Test
	public void testMorphToEnd() throws Exception {

		final String expected = DMPUtil.getResourceAsString("complex-result.json");

		final TransformationFlow flow = TransformationFlow.fromFile("complex-metamorph.xml");

		final String actual = flow.apply();

		assertEquals(expected, actual);
	}

	@Test
	public void testEndToEndByRecordStringExample() throws Exception {

		final String request = DMPUtil.getResourceAsString("qucosa_record.xml");
		final String expected = DMPUtil.getResourceAsString("complex-result.json");

		final TransformationFlow flow = TransformationFlow.fromFile("complex-metamorph.xml");

		final String actual = flow.applyRecord(request);

		assertEquals(expected, actual);
	}

	@Test
	public void readCSVTest() throws Exception {

		StringReader opener = new StringReader();
		final String testCSVString = DMPUtil.getResourceAsString("test_csv.csv");

		final CsvReader reader = new CsvReader();
		reader.setHeaderLines(1);
		final JsonEncoder converter = new JsonEncoder();
		final StringWriter stringWriter = new StringWriter();
		final ObjectJavaIoWriter<String> writer = new ObjectJavaIoWriter<String>(stringWriter);

		opener.setReceiver(reader).setReceiver(converter).setReceiver(writer);

		opener.process(testCSVString);

		final String resultOutput = stringWriter.toString();

		Assert.assertNotNull("the result output shoudln't be null", resultOutput);
		
		final String expectedResult = DMPUtil.getResourceAsString("csv_json.output");
		
		Assert.assertEquals("the processing outputs are not equal", expectedResult, resultOutput);
	}
}
