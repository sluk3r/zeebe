package org.camunda.tngp.perftest;

import static org.camunda.tngp.test.util.bpmn.TngpModelInstance.wrap;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.client.cmd.WorkflowDefinition;
import org.camunda.tngp.perftest.helper.FixedRateLatencyTest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;

public class StartWorkflowInstanceLatencyTest extends FixedRateLatencyTest
{
    protected long workflowDefinitionId;

    @Override
    protected void executeSetup(Properties properties, TngpClient client)
    {
        final WorkflowTopicClient workflowsClient = client.workflowTopic();

        final BpmnModelInstance processModel = Bpmn.createExecutableProcess()
                .startEvent()
                .serviceTask("serviceTask")
                .endEvent()
                .done();

        wrap(processModel).taskAttributes("serviceTask", "foo", 0);

        final WorkflowDefinition deployedWorkflow = workflowsClient
            .deploy()
            .bpmnModelInstance(processModel)
            .execute();

        workflowDefinitionId = deployedWorkflow.getId();

        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Supplier<Future> requestFn(TngpClient client, TransportConnection connection)
    {
        final WorkflowTopicClient workflowsClient = client.workflowTopic();

        return () ->
        {
            return workflowsClient.start()
                .workflowDefinitionId(workflowDefinitionId)
                .executeAsync(connection);
        };
    }

    public static void main(String[] args)
    {
        new StartWorkflowInstanceLatencyTest().run();
    }
}
