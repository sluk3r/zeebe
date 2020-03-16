/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import static io.zeebe.util.EnsureUtil.ensureGreaterThan;

import io.zeebe.el.EvaluationContext;
import io.zeebe.el.EvaluationResult;
import io.zeebe.el.Expression;
import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.ResultType;
import io.zeebe.engine.state.instance.VariablesState;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExpressionProcessor {

  private final DirectBuffer resultView = new UnsafeBuffer();

  private final ExpressionLanguage expressionLanguage;
  private final VariableStateEvaluationContext evaluationContext;

  public ExpressionProcessor(
      final ExpressionLanguage expressionLanguage, final VariablesState variablesState) {
    this.expressionLanguage = expressionLanguage;

    evaluationContext = new VariableStateEvaluationContext(variablesState);
  }

  /**
   * Evaluates the given expression and returns the result as string wrapped in {@link
   * DirectBuffer}. If the evaluation fails or the result is not a string then an incident is
   * raised.
   *
   * @param expression the expression to evaluate
   * @param context the element context to load the variables from
   * @return the evaluation result as buffer, or {@link Optional#empty()} if an incident is raised
   */
  public Optional<DirectBuffer> evaluateStringExpression(
      final Expression expression, final BpmnStepContext<?> context) {

    return evaluateExpression(
            expression,
            context.getKey(),
            new ExpressionResultTypeVerifyingIncidentRaisingHandler<>(
                ResultType.STRING, context, EvaluationResult::getString))
        .map(this::wrapResult);
  }

  /**
   * Evaluates the given expression and returns the result as boolean. If the evaluation fails or
   * the result is not a boolean then an incident is raised.
   *
   * @param expression the expression to evaluate
   * @param context the element context to load the variables from
   * @return the evaluation result as boolean, or {@link Optional#empty()} if an incident is raised
   */
  public Optional<Boolean> evaluateBooleanExpression(
      final Expression expression, final BpmnStepContext<?> context) {

    return evaluateExpression(
        expression,
        context.getKey(),
        new ExpressionResultTypeVerifyingIncidentRaisingHandler<Boolean>(
            ResultType.BOOLEAN, context, EvaluationResult::getBoolean));
  }

  public <T> T evaluateExpression(
      final Expression expression,
      final long variableScopeKey,
      final ExpressionResultHandler<T> resultHandler) {

    final var evaluationResult = evaluateExpression(expression, variableScopeKey);

    return resultHandler.handleEvaluationResult(evaluationResult);
  }

  private EvaluationResult evaluateExpression(
      final Expression expression, final long variableScopeKey) {

    evaluationContext.variableScopeKey = variableScopeKey;

    return expressionLanguage.evaluateExpression(expression, evaluationContext);
  }

  private DirectBuffer wrapResult(final String result) {
    resultView.wrap(result.getBytes());
    return resultView;
  }

  private static class VariableStateEvaluationContext implements EvaluationContext {

    private final DirectBuffer variableNameBuffer = new UnsafeBuffer();

    private final VariablesState variablesState;

    private long variableScopeKey;

    public VariableStateEvaluationContext(final VariablesState variablesState) {
      this.variablesState = variablesState;
    }

    @Override
    public DirectBuffer getVariable(final String variableName) {
      ensureGreaterThan("variable scope key", variableScopeKey, 0);

      variableNameBuffer.wrap(variableName.getBytes());

      return variablesState.getVariable(variableScopeKey, variableNameBuffer);
    }
  }
}
