/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sahli.asciidoc.confluence.publisher.client;


import io.restassured.specification.RequestSpecification;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.Test;
import org.sahli.asciidoc.confluence.publisher.client.http.ConfluenceRestClient;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePageMetadata;
import org.sahli.asciidoc.confluence.publisher.client.metadata.ConfluencePublisherMetadata;

import static io.restassured.RestAssured.given;
import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

/**
 * @author Alain Sahli
 * @author Christian Stettler
 */
public class ConfluencePublisherIntegrationTest {

    private static final String ANCESTOR_ID = "327706";

    @Test
    public void publish_singlePage_pageIsCreatedInConfluence() {
        // arrange
        String title = uniqueTitle("Single Page");
        ConfluencePageMetadata confluencePageMetadata = confluencePageMetadata(title, "single-page.xhtml");
        ConfluencePublisherMetadata confluencePublisherMetadata = confluencePublisherMetadata(confluencePageMetadata);

        ConfluencePublisher confluencePublisher = confluencePublisher(confluencePublisherMetadata, "single-page");

        // act
        confluencePublisher.publish();

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(childPages())
                .then().body("results.title", hasItem(title));
    }

    @Test
    public void publish_sameContentPublishedMultipleTimes_doesNotProduceMultipleVersions() throws Exception {
        // arrange
        String title = uniqueTitle("Single Page");
        ConfluencePageMetadata confluencePageMetadata = confluencePageMetadata(title, "single-page.xhtml");
        ConfluencePublisherMetadata confluencePublisherMetadata = confluencePublisherMetadata(confluencePageMetadata);
        ConfluencePublisher confluencePublisher = confluencePublisher(confluencePublisherMetadata, "single-page");

        // act
        confluencePublisher.publish();
        confluencePublisher.publish();

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(pageVersionOf(pageIdBy(title)))
                .then().body("version.number", is(1));
    }

    @Test
    public void publish_validPageContentThenInvalidPageContentThenValidContentAgain_validPageContentWithNonEmptyContentHashIsInConfluenceAtTheEndOfPublication() throws Exception {
        // arrange
        String title = uniqueTitle("Invalid Markup Test Page");
        ConfluencePageMetadata confluencePageMetadata = confluencePageMetadata(title, "single-page.xhtml");
        ConfluencePublisherMetadata confluencePublisherMetadata = confluencePublisherMetadata(confluencePageMetadata);
        ConfluencePublisher confluencePublisher = confluencePublisher(confluencePublisherMetadata, "single-page");

        // act
        confluencePublisher.publish();

        confluencePageMetadata.setContentFilePath("invalid-xhtml.xhtml");
        try {
            confluencePublisher.publish();
            fail("publish with invalid XHTML is expected to fail");
        } catch (Exception ignored) {
        }

        confluencePageMetadata.setContentFilePath("single-page.xhtml");
        confluencePublisher.publish();

        // assert
        givenAuthenticatedAsPublisher()
                .when().get(propertyValueOf(pageIdBy(title), "content-hash"))
                .then().body("value", is(notNullValue()));
    }

    private static String uniqueTitle(String title) {
        return title + " - " + randomUUID().toString();
    }

    private static ConfluencePageMetadata confluencePageMetadata(String title, String contentFilePath) {
        ConfluencePageMetadata confluencePageMetadata = new ConfluencePageMetadata();
        confluencePageMetadata.setTitle(title);
        confluencePageMetadata.setContentFilePath(contentFilePath);

        return confluencePageMetadata;
    }

    private static ConfluencePublisherMetadata confluencePublisherMetadata(ConfluencePageMetadata... pages) {
        ConfluencePublisherMetadata confluencePublisherMetadata = new ConfluencePublisherMetadata();
        confluencePublisherMetadata.setSpaceKey("CPI");
        confluencePublisherMetadata.setAncestorId(ANCESTOR_ID);
        confluencePublisherMetadata.setPages(asList(pages));

        return confluencePublisherMetadata;
    }

    private static String childPages() {
        return "http://localhost:8090/rest/api/content/" + ANCESTOR_ID + "/child/page";
    }

    private static String pageVersionOf(String contentId) {
        return "http://localhost:8090/rest/api/content/" + contentId + "?expand=version";
    }

    private static String propertyValueOf(String contentId, String key) {
        return "http://localhost:8090/rest/api/content/" + contentId + "/property/" + key;
    }

    private static String pageIdBy(String title) {
        return givenAuthenticatedAsPublisher()
                .when().get(childPages())
                .then().extract().jsonPath().getString("results.find({it.title == '" + title + "'}).id");
    }

    private static ConfluencePublisher confluencePublisher(ConfluencePublisherMetadata confluencePublisherMetadata, String contentRoot) {
        return new ConfluencePublisher(confluencePublisherMetadata, confluenceRestClient(), "src/it/resources/" + contentRoot);
    }

    private static RequestSpecification givenAuthenticatedAsPublisher() {
        return given().auth().preemptive().basic("confluence-publisher-it", "1234");
    }

    private static ConfluenceRestClient confluenceRestClient() {
        return new ConfluenceRestClient("http://localhost:8090", httpClient(), "confluence-publisher-it", "1234");
    }

    private static CloseableHttpClient httpClient() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(20 * 1000)
                .setConnectTimeout(20 * 1000)
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

}
