import java.util.ArrayList;

import org.irislang.jiris.compiler.IrisInterpreter;

import com.irisine.jiris.compiler.IrisCompiler;



public class Main {
	
	static final boolean TEST = false;
	
	public static void main(String[] argv) throws Throwable {
				
		if(!IrisInterpreter.INSTANCE.Initialize()) {
			IrisInterpreter.INSTANCE.ShutDown();
		}
		
		if(TEST) {
			IrisCompiler.INSTANCE.TestLoad("test.ir");			
		} else {
			if(!IrisCompiler.INSTANCE.LoadScriptFromPath("test.ir")){
				return;
			}
				
			IrisInterpreter.INSTANCE.setCurrentCompiler(IrisCompiler.INSTANCE);
			
			IrisInterpreter.INSTANCE.Run();
			
			IrisInterpreter.INSTANCE.ShutDown();	
		}

		//VerifyBrace(null);
	}
	
/*	static private void VerifyBrace(ArrayList<String> irisScriptText) throws Exception {
		// ���������ǲ���д����һ��
		for(String line : irisScriptText) {
			// ���һ�д����Ƿ��Դ����ſ�ͷ����������׳��쳣��ֹͣ����
			if(line.trim().startsWith("{")) {
				throw(new Exception("Stupid code, you motherfucker."));
			}
		}
	}*/
}
