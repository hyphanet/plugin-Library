package freenet.library.util;

import freenet.library.util.concurrent.ExceptionConvertor;
import freenet.library.util.exec.TaskAbortException;

public class TaskAbortExceptionConvertor implements
		ExceptionConvertor<TaskAbortException> {

	public TaskAbortException convert(RuntimeException e) {
		return new TaskAbortException(e.getMessage(), e);
	}

}
