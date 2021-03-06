/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.api.frontend.resources;

import com.vmware.photon.controller.api.frontend.clients.TaskFeClient;
import com.vmware.photon.controller.api.frontend.commands.tasks.TaskCommandFactory;
import com.vmware.photon.controller.api.frontend.config.PaginationConfig;
import com.vmware.photon.controller.api.frontend.resources.routes.ResourceTicketResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.tasks.ResourceTicketTasksResource;
import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Tests {@link com.vmware.photon.controller.api.frontend.resources.tasks.ResourceTicketTasksResource}.
 */
public class ResourceTicketTasksResourceTest extends ResourceTest {

  private String ticketId = "rt1";
  private String resourceTicketTasksRoutePath =
      UriBuilder.fromPath(ResourceTicketResourceRoutes.RESOURCE_TICKET_TASKS_PATH).build(ticketId).toString();

  private String taskId1 = "task1";
  private String taskRoutePath1 =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId1).toString();
  private Task task1 = new Task();

  private String taskId2 = "task2";
  private String taskRoutePath2 =
      UriBuilder.fromPath(TaskResourceRoutes.TASK_PATH).build(taskId2).toString();
  private Task task2 = new Task();

  private PaginationConfig paginationConfig = new PaginationConfig();

  @Mock
  private TaskFeClient client;

  @Mock
  private TaskCommandFactory taskCommandFactory;

  @Override
  protected void setUpResources() throws Exception {
    paginationConfig.setDefaultPageSize(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE);
    paginationConfig.setMaxPageSize(PaginationConfig.DEFAULT_MAX_PAGE_SIZE);

    addResource(new ResourceTicketTasksResource(client, paginationConfig));
  }

  @Test(dataProvider = "pageSizes")
  public void testGetResourceTicketTasks(Optional<Integer> pageSize,
                                         List<Task> expectedTasks,
                                         List<String> expectedTaskRoutes) throws Exception {
    task1.setId(taskId1);
    task2.setId(taskId2);

    when(client.getResourceTicketTasks(ticketId, Optional.<String>absent(),
        Optional.of(PaginationConfig.DEFAULT_DEFAULT_PAGE_SIZE)))
        .thenReturn(new ResourceList<>(ImmutableList.of(task1, task2), null, null));
    when(client.getResourceTicketTasks(ticketId, Optional.<String>absent(), Optional.of(1)))
        .thenReturn(new ResourceList<>(ImmutableList.of(task1), UUID.randomUUID().toString(), null));
    when(client.getResourceTicketTasks(ticketId, Optional.<String>absent(), Optional.of(2)))
        .thenReturn(new ResourceList<>(ImmutableList.of(task1, task2), null, null));
    when(client.getResourceTicketTasks(ticketId, Optional.<String>absent(), Optional.of(3)))
        .thenReturn(new ResourceList<>(Collections.emptyList(), null, null));

    Response response = getTasks(pageSize);
    assertThat(response.getStatus(), is(200));

    ResourceList<Task> tasks = response.readEntity(
        new GenericType<ResourceList<Task>>() {
        }
    );

    assertThat(tasks.getItems().size(), is(expectedTasks.size()));

    for (int i = 0; i < tasks.getItems().size(); i++) {
      assertThat(tasks.getItems().get(i), is(expectedTasks.get(i)));
      assertThat(new URI(tasks.getItems().get(i).getSelfLink()).isAbsolute(), CoreMatchers.is(true));
      assertThat(tasks.getItems().get(i).getSelfLink().endsWith(expectedTaskRoutes.get(i)), CoreMatchers.is(true));
    }

    verifyPageLinks(tasks);
  }

  @Test
  public void testGetResourceTicketTasksPage() throws Exception {
    ResourceList<Task> expectedTasksPage = new ResourceList<Task>(ImmutableList.of(task1, task2),
        UUID.randomUUID().toString(), UUID.randomUUID().toString());
    when(client.getPage(anyString())).thenReturn(expectedTasksPage);

    List<String> expectedSelfLinks = ImmutableList.of(taskRoutePath1, taskRoutePath2);

    Response response = getTasks(UUID.randomUUID().toString());
    assertThat(response.getStatus(), is(200));

    ResourceList<Task> tasks = response.readEntity(
        new GenericType<ResourceList<Task>>() {
        }
    );

    assertThat(tasks.getItems().size(), is(expectedTasksPage.getItems().size()));

    for (int i = 0; i < tasks.getItems().size(); i++) {
      assertThat(new URI(tasks.getItems().get(i).getSelfLink()).isAbsolute(), is(true));
      assertThat(tasks.getItems().get(i), is(expectedTasksPage.getItems().get(i)));
      assertThat(tasks.getItems().get(i).getSelfLink().endsWith(expectedSelfLinks.get(i)), is(true));
    }

    verifyPageLinks(tasks);
  }

  @Test
  public void testInvalidPageSize() {
    Response response = getTasks(Optional.of(200));
    assertThat(response.getStatus(), is(400));

    ApiError errors = response.readEntity(ApiError.class);
    assertThat(errors.getCode(), is("InvalidPageSize"));
    assertThat(errors.getMessage(), is("The page size '200' is not between '1' and '100'"));
  }

  @DataProvider(name = "pageSizes")
  private Object[][] getPageSize() {
    return new Object[][] {
        {
            Optional.<Integer>absent(),
            ImmutableList.of(task1, task2),
            ImmutableList.of(taskRoutePath1, taskRoutePath2)
        },
        {
            Optional.of(1),
            ImmutableList.of(task1),
            ImmutableList.of(taskRoutePath1)
        },
        {
            Optional.of(2),
            ImmutableList.of(task1, task2),
            ImmutableList.of(taskRoutePath1, taskRoutePath2)
        },
        {
            Optional.of(3),
            Collections.emptyList(),
            Collections.emptyList()
        }
    };
  }

  private Response getTasks(Optional<Integer> pageSize) {
    String uri = resourceTicketTasksRoutePath;
    if (pageSize.isPresent()) {
      uri += "?pageSize=" + pageSize.get();
    }

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private Response getTasks(String pageLink) {
    String uri = resourceTicketTasksRoutePath + "?pageLink=" + pageLink;

    WebTarget resource = client().target(uri);
    return resource.request().get();
  }

  private void verifyPageLinks(ResourceList<Task> resourceList) {
    String expectedPrefix = TaskResourceRoutes.API + "?pageLink=";

    if (resourceList.getNextPageLink() != null) {
      assertThat(resourceList.getNextPageLink().startsWith(expectedPrefix), is(true));
    }
    if (resourceList.getPreviousPageLink() != null) {
      assertThat(resourceList.getPreviousPageLink().startsWith(expectedPrefix), is(true));
    }
  }
}
