/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import io.zeebe.el.EvaluationResult;
import io.zeebe.el.ResultType;
import io.zeebe.protocol.record.value.ErrorType;
import java.util.Optional;
import java.util.function.Function;

/**
 * This expression result handler raises an incident if the evaluation resulted in a failure or an
 * unexpected type
 *
 * @param <T> target type to which the result of the expression shall be converted
 */
public final class ExpressionResultTypeVerifyingIncidentRaisingHandler<T>
    implements ExpressionResultHandler<Optional<T>> {

  final ResultType expectedResultType;
  final Function<EvaluationResult, T> resultExtractor;
  final BpmnStepContext<?> context;

  public ExpressionResultTypeVerifyingIncidentRaisingHandler(
      final ResultType expectedResultType,
      final BpmnStepContext<?> context,
      final Function<EvaluationResult, T> resultExtractor) {
    this.expectedResultType = expectedResultType;
    this.context = context;

    this.resultExtractor = resultExtractor;
  }

  @Override
  public Optional<T> handleEvaluationResult(final EvaluationResult evaluationResult) {
    if (evaluationResult.isFailure()) {
      context.raiseIncident(ErrorType.EXTRACT_VALUE_ERROR, evaluationResult.getFailureMessage());
      return Optional.empty();
    }

    if (evaluationResult.getType() != expectedResultType) {
      context.raiseIncident(
          ErrorType.EXTRACT_VALUE_ERROR,
          String.format(
              "Expected result of the expression '%s' to be '%s', but was '%s'.",
              evaluationResult.getExpression(), expectedResultType, evaluationResult.getType()));
      return Optional.empty();
    }

    return Optional.of(resultExtractor.apply(evaluationResult));
  }
}
