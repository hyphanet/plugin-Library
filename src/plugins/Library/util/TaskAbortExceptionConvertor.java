package plugins.Library.util;

import freenet.library.util.exec.TaskAbortException;
import plugins.Library.util.concurrent.ExceptionConvertor;

public class TaskAbortExceptionConvertor implements
		ExceptionConvertor<TaskAbortException> {

	public TaskAbortException convert(RuntimeException e) {
		return new TaskAbortException(e.getMessage(), e);
	}

}
