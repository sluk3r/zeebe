package org.camunda.tngp.client.impl.cmd.taskqueue;

import static org.camunda.tngp.protocol.clientapi.EventType.TASK_EVENT;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.camunda.tngp.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.util.EnsureUtil;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CreateTaskCmdImpl extends AbstractExecuteCmdImpl<TaskEvent, Long> implements CreateAsyncTaskCmd
{
    protected final TaskEvent taskEvent = new TaskEvent();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected String taskType;
    protected byte[] payload;
    protected Map<String, String> headers = new HashMap<>();

    public CreateTaskCmdImpl(final ClientCmdExecutor clientCmdExecutor, final ObjectMapper objectMapper, final int topicId)
    {
        super(clientCmdExecutor, objectMapper, TaskEvent.class, topicId, TASK_EVENT);
    }

    @Override
    public CreateAsyncTaskCmd taskType(final String taskType)
    {
        this.taskType = taskType;
        return this;
    }

    @Override
    public CreateAsyncTaskCmd payload(String payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public CreateAsyncTaskCmd payload(InputStream payload)
    {
        this.payload = msgPackConverter.convertToMsgPack(payload);
        return this;
    }

    @Override
    public CreateAsyncTaskCmd addHeader(String key, String value)
    {
        headers.put(key, value);
        return this;
    }

    @Override
    public CreateAsyncTaskCmd setHeaders(Map<String, String> headers)
    {
        this.headers.clear();
        this.headers.putAll(headers);
        return this;
    }

    @Override
    protected long getKey()
    {
        return -1L;
    }

    @Override
    public void validate()
    {
        EnsureUtil.ensureGreaterThanOrEqual("topic id", topicId, 0);
        EnsureUtil.ensureNotNullOrEmpty("task type", taskType);
    }

    @Override
    protected Object writeCommand()
    {
        taskEvent.setEvent(TaskEventType.CREATE);
        taskEvent.setType(taskType);
        taskEvent.setHeaders(headers);
        taskEvent.setPayload(payload);

        return taskEvent;
    }

    @Override
    protected void reset()
    {
        taskType = null;
        payload = null;
        headers.clear();

        taskEvent.reset();
    }

    @Override
    protected Long getResponseValue(long key, TaskEvent event)
    {
        return key;
    }

}
