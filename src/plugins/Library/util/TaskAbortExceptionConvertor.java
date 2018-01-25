package plugins.Library.util;

import plugins.Library.util.concurrent.ExceptionConvertor;
import plugins.Library.util.exec.TaskAbortException;

public class TaskAbortExceptionConvertor implements ExceptionConvertor<TaskAbortException> {

  public TaskAbortException convert(RuntimeException e) {
    return new TaskAbortException(e.getMessage(), e);
  }

}
