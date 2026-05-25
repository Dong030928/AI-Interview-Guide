package interview.guide.modules.monitor.aspect;

import interview.guide.modules.monitor.model.OperationEventType;
import interview.guide.modules.monitor.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationLogAspect {

  private final OperationLogService operationLogService;

  /**
   * 捕获异步任务消费失败（AbstractStreamConsumer.markFailed）
   */
  @AfterThrowing(
      pointcut = "execution(* interview.guide.common.async.AbstractStreamConsumer.markFailed(..))",
      throwing = "ex")
  public void onAsyncTaskFailed(JoinPoint joinPoint, Throwable ex) {
    String source = joinPoint.getTarget().getClass().getSimpleName();
    Object[] args = joinPoint.getArgs();
    String identifier = args.length > 0 ? String.valueOf(args[0]) : "unknown";
    String errorMsg = args.length > 1 ? String.valueOf(args[1]) : ex.getMessage();

    operationLogService.recordEvent(
        OperationEventType.ASYNC_TASK,
        "ERROR",
        source,
        "异步任务失败: " + identifier + " - " + errorMsg,
        ex,
        null,
        "{\"taskIdentifier\":\"" + identifier + "\"}");
  }
}
