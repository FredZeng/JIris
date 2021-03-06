package org.irislang.jiris.compiler;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

import org.irislang.jiris.core.*;
import org.irislang.jiris.core.IrisMethod.IrisUserMethod;
import org.irislang.jiris.core.IrisContextEnvironment.RunTimeType;
import org.irislang.jiris.core.exceptions.IrisExceptionBase;
import org.irislang.jiris.core.exceptions.fatal.*;
import org.irislang.jiris.dev.IrisDevUtil;
import org.irislang.jiris.irisclass.IrisClassBase;
import org.irislang.jiris.irisclass.IrisModuleBase;

import javax.naming.Name;

public abstract class IrisNativeJavaClass {

	public static CallSite BootstrapMethod(Class<?> classObj, MethodHandles.Lookup lookup, String name, MethodType mt) throws Throwable {
        return new ConstantCallSite(lookup.findStatic(classObj, name, mt));
    }

    protected static IrisValue GetFieldValue(IrisValue headValue, String[] pathConstance, String lastConstance)
            throws IrisExceptionBase {
        IrisValue currentValue = headValue;
        IrisModule currentModule = null;

        if(IrisDevUtil.CheckClass(currentValue, "Module")) {
            currentModule = ((IrisModuleBase.IrisModuleBaseTag)IrisDevUtil.GetNativeObjectRef(currentValue)).getModule();
        }
        else {
            // Error
            throw new IrisInvalidFieldException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                    IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(),
                    "Head value is not a module!");
        }

        if(pathConstance != null) {
            for(String identifier : pathConstance) {
                currentValue = currentModule.SearchConstance(identifier);
                if(currentValue == null) {
                    // Error
                    throw new IrisInvalidFieldException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                            IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(),
                            "Inner constance of " + pathConstance +  " not found!");
                }

                if(!IrisDevUtil.CheckClass(currentValue, "Module")) {
                    // Error
                    throw new IrisInvalidFieldException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                            IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(),
                            "Inner constance of " + pathConstance +  " is not a module!");
                }

                currentModule = ((IrisModuleBase.IrisModuleBaseTag)IrisDevUtil.GetNativeObjectRef(currentValue)).getModule();
            }
        }
        
        currentValue = currentModule.SearchConstance(lastConstance);
        if(currentValue == null) {
            throw new IrisInvalidFieldException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                    IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(),
                    "Last inner constance of " + lastConstance +  " is not a module!");
        }

        return currentValue;
    }

	protected static IrisValue CallMethod(IrisValue object, String methodName, IrisThreadInfo threadInfo, IrisContextEnvironment context, int parameterCount) throws IrisExceptionBase {
		IrisValue result = null;
		// hide call
		if(object == null) {
			// main
			if(context.getRunTimeType() != RunTimeType.RunTime) {
				IrisMethod method = IrisInterpreter.INSTANCE.GetMainMethod(methodName);
				if(method == null) {
  					result = IrisDevUtil.GetModule("Kernel").getModuleObject().CallInstanceMethod(methodName, threadInfo.getPartPrameterListOf(parameterCount), context, threadInfo, IrisMethod.CallSide.Outeside);
				} else {
					result = method.CallMain(threadInfo.getPartPrameterListOf(parameterCount), context, threadInfo);
				}
			} else {
				if(context.getRunningType() != null) {
					result = ((IrisObject)context.getRunningType()).CallInstanceMethod(methodName, threadInfo.getPartPrameterListOf(parameterCount), context, threadInfo, IrisMethod.CallSide.Inside);
				} else {
					IrisMethod method = IrisInterpreter.INSTANCE.GetMainMethod(methodName);
					if(method == null) {
						result = IrisDevUtil.GetModule("Kernel").getModuleObject().CallInstanceMethod(methodName, threadInfo.getPartPrameterListOf(parameterCount), context, threadInfo, IrisMethod.CallSide.Outeside);
					} else {
						result = method.CallMain(threadInfo.getPartPrameterListOf(parameterCount), context, threadInfo);
					}
				}
			}
		} else {
			// normal call
			result = object.getObject().CallInstanceMethod(methodName, threadInfo.getPartPrameterListOf(parameterCount), context, threadInfo, IrisMethod.CallSide.Outeside);
		}
		return result;
	}

	protected static IrisValue GetLocalVariable(String variableName, IrisThreadInfo threadInfo, IrisContextEnvironment context) {
		IrisValue value = context.getClosureBlockObj() != null ?
                ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).GetLocalVariable(variableName)
                : context.GetLocalVariable(variableName);
		if(value == null) {
			value = IrisValue.CloneValue(IrisDevUtil.Nil());
            if(context.getClosureBlockObj() != null){
                ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).AddLocalVariable(variableName, value);
            }
            else {
                context.AddLocalVariable(variableName, value);
            }
		}
		else {
			value = IrisValue.CloneValue(value);
		}
		return IrisValue.CloneValue(value);
	}

	protected static IrisValue SetLocalVariable(String variableName, IrisValue value, IrisThreadInfo threadInfo, IrisContextEnvironment context) {
		IrisValue testValue = context.getClosureBlockObj() != null ?
                        ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).GetLocalVariable(variableName)
                        : context.GetLocalVariable(variableName);
		if(testValue == null) {
		    if(context.getClosureBlockObj() != null) {
                ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).AddLocalVariable(variableName, IrisValue.CloneValue(value));
            }
            else {
                context.AddLocalVariable(variableName, IrisValue.CloneValue(value));
            }
		} else {
			testValue.setObject(value.getObject());
		}
		return value;
	}

	protected static IrisValue GetClassVariable(String variableName, IrisThreadInfo threadInfo,
                                                IrisContextEnvironment context) throws IrisExceptionBase {
		IrisValue value = null;
		// Main context
		if(context.getRunningType() == null) {
			value = context.getClosureBlockObj() != null ?
                    ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).GetClassVariable(variableName)
                    : context.GetLocalVariable(variableName);
			if(value == null) {
				value = IrisValue.CloneValue(IrisDevUtil.Nil());
				if(context.getClosureBlockObj() != null) {
                    ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).AddLocalVariable(variableName, IrisValue.CloneValue(value));
                }
                else {
                    context.AddLocalVariable(variableName, value);
                }
			}
			else {
				value = IrisValue.CloneValue(value);
			}
		}
		// Class Type
		else {
			switch(context.getRunTimeType()) {
			case ClassDefineTime :
				value = ((IrisClass)context.getRunningType()).SearchClassVariable(variableName);
				if(value == null) {
					value = IrisValue.CloneValue(IrisDevUtil.Nil());
					((IrisClass)context.getRunningType()).AddClassVariable(variableName, value);
				}
				else {
					value = IrisValue.CloneValue(value);
				}
				break;
			case ModuleDefineTime :
				value = ((IrisModule)context.getRunningType()).SearchClassVariable(variableName);
				if(value == null) {
					value = IrisValue.CloneValue(IrisDevUtil.Nil());
					((IrisModule)context.getRunningType()).AddClassVariable(variableName, value);
				}
				else {
					value = IrisValue.CloneValue(value);
				}
				break;
			case InterfaceDefineTime :
				/* Error */
				throw new IrisVariableImpossiblyExistsException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                        IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Class variable won't exist in " +
                        "interface");
			case RunTime :
				value = ((IrisClass)context.getRunningType()).SearchClassVariable(variableName);
				if(value == null) {
					value = IrisValue.CloneValue(IrisDevUtil.Nil());
					((IrisClass)context.getRunningType()).AddClassVariable(variableName, value);
				}
				else {
					value = IrisValue.CloneValue(value);
				}
				break;
			}
		}
		return value;
	}

	protected static IrisValue SetClassVariable(String variableName, IrisValue value, IrisThreadInfo threadInfo,
                                                IrisContextEnvironment context) throws IrisExceptionBase {
		IrisValue testValue = null;
		// Main context
		if(context.getRunningType() == null) {
			//testValue = context.GetLocalVariable(variableName);
            testValue = context.getClosureBlockObj() != null ?
                    ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).GetClassVariable(variableName)
                    : context.GetLocalVariable(variableName);
			if(testValue == null) {
			    //context.AddLocalVariable(variableName, IrisValue.CloneValue(value));
                if(context.getClosureBlockObj() != null) {
                    ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).AddLocalVariable(variableName, IrisValue.CloneValue(value));
                }
                else {
                    context.AddLocalVariable(variableName, IrisValue.CloneValue(value));
                }
            } else {
				testValue.setObject(value.getObject());
			}
		}
		else {
			switch(context.getRunTimeType()) {
			case ClassDefineTime :
				testValue = ((IrisClass)context.getRunningType()).SearchClassVariable(variableName);
				if(testValue == null) {
					((IrisClass)context.getRunningType()).AddClassVariable(variableName, IrisValue.CloneValue(value));
				} else {
					testValue.setObject(value.getObject());
				}
				break;
			case ModuleDefineTime :
				testValue = ((IrisModule)context.getRunningType()).SearchClassVariable(variableName);
				if(testValue == null) {
					((IrisModule)context.getRunningType()).AddClassVariable(variableName, IrisValue.CloneValue(value));
				} else {
					testValue.setObject(value.getObject());
				}
				break;
			case InterfaceDefineTime :
				/* Error */
                throw new IrisVariableImpossiblyExistsException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                        IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Class variable won't be defined " +
                        "in interface");
			case RunTime :
				testValue = ((IrisClass)context.getRunningType()).SearchClassVariable(variableName);
				if(testValue == null) {
					((IrisClass)context.getRunningType()).AddClassVariable(variableName, IrisValue.CloneValue(value));
				} else {
					testValue.setObject(value.getObject());
				}
				break;
			}
		}

		return value;
	}

	protected static IrisValue GetConstance(String variableName, IrisThreadInfo threadInfo, IrisContextEnvironment
            context) throws  IrisExceptionBase {
		IrisValue value = null;
		if(context.getRunningType() == null) {
			//value = IrisInterpreter.INSTANCE.GetConstance(variableName);
            value = context.getClosureBlockObj() != null ?
                    ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).GetConstance(variableName)
                    : IrisInterpreter.INSTANCE.GetConstance(variableName);
            if(value == null) {
				value = IrisValue.CloneValue(IrisDevUtil.Nil());
				//IrisInterpreter.INSTANCE.AddConstance(variableName, value);
                if(context.getClosureBlockObj() != null) {
                    //((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).AddLocalVariable(variableName, IrisValue.CloneValue(value));
                    throw new IrisVariableImpossiblyExistsException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                            IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Constance won't be declared in Block.");
                }
                else {
                    IrisInterpreter.INSTANCE.AddConstance(variableName, IrisValue.CloneValue(value));
                }
            }
			else {
				value = IrisValue.CloneValue(value);
			}
		}
		else {
			switch(context.getRunTimeType()) {
				case ClassDefineTime :
					value = ((IrisClass)context.getRunningType()).SearchConstance(variableName);
                    if(value == null) {
                        value = IrisInterpreter.INSTANCE.GetConstance(variableName);
                    }

                    if(value == null) {
                        value = IrisValue.CloneValue(IrisDevUtil.Nil());
						((IrisClass)context.getRunningType()).AddConstance(variableName, value);
					}
					else {
						value = IrisValue.CloneValue(value);
					}
					break;
				case ModuleDefineTime :
					value = ((IrisModule)context.getRunningType()).SearchConstance(variableName);
                    if(value == null) {
                        value = IrisInterpreter.INSTANCE.GetConstance(variableName);
                    }

                    if(value == null) {
						value = IrisValue.CloneValue(IrisDevUtil.Nil());
						((IrisModule)context.getRunningType()).AddConstance(variableName, value);
					}
					else {
						value = IrisValue.CloneValue(value);
					}
					break;
				case InterfaceDefineTime :
				    /* Error */
                    throw new IrisVariableImpossiblyExistsException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                            IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Constance won't exist in interface.");
				case RunTime :
					value = ((IrisObject)context.getRunningType()).getObjectClass().SearchConstance(variableName);
                    if(value == null) {
                        value = IrisInterpreter.INSTANCE.GetConstance(variableName);
                    }

                    if(value == null) {
						value = IrisValue.CloneValue(IrisDevUtil.Nil());
						((IrisObject)context.getRunningType()).getObjectClass().AddConstance(variableName, value);
					}
					else {
						value = IrisValue.CloneValue(value);
					}
					break;
			}
		}
		return value;
	}

	protected static IrisValue SetConstance(String variableName, IrisValue value, IrisThreadInfo threadInfo,
                                            IrisContextEnvironment context) throws IrisExceptionBase {
		IrisValue testValue = null;
		if(context.getRunningType() == null) {
			//testValue = IrisInterpreter.INSTANCE.GetConstance(variableName);
            testValue = context.getClosureBlockObj() != null ?
                    ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).GetConstance(variableName)
                    : IrisInterpreter.INSTANCE.GetConstance(variableName);
			if(testValue == null) {
				//IrisInterpreter.INSTANCE.AddConstance(variableName, IrisValue.CloneValue(value));
                if(context.getClosureBlockObj() != null) {
                    //((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).AddLocalVariable(variableName, IrisValue.CloneValue(value));
                    throw new IrisVariableImpossiblyExistsException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                            IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Constance won't be declared in Block.");
                }
                else {
                    IrisInterpreter.INSTANCE.AddConstance(variableName, IrisValue.CloneValue(value));
                }
            } else {
				/* Error */
				throw new IrisConstanceReassignedException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                        IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Constance of" + variableName +
                        "has already assigned.");
			}
		}
		else {
			switch(context.getRunTimeType()) {
			case ClassDefineTime :
				testValue = ((IrisClass)context.getRunningType()).GetConstance(variableName);
				if(testValue == null) {
					((IrisClass)context.getRunningType()).AddConstance(variableName, IrisValue.CloneValue(value));
				} else {
					/* Error */
                    throw new IrisConstanceReassignedException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                            IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Constance of" + variableName +
                            "has already assigned.");
				}
				break;
			case ModuleDefineTime :
				testValue = ((IrisModule)context.getRunningType()).GetConstance(variableName);
				if(testValue == null) {
					((IrisModule)context.getRunningType()).AddConstance(variableName, IrisValue.CloneValue(value));
				} else {
					/* Error */
                    throw new IrisConstanceReassignedException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                            IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Constance of" + variableName +
                            "has already assigned.");
				}
				break;
			case InterfaceDefineTime :
				/* Error */
                throw new IrisVariableImpossiblyExistsException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                        IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Constance can not be defined in " +
                        "interface");
			case RunTime :
				testValue = ((IrisObject)context.getRunningType()).getObjectClass().GetConstance(variableName);
				if(testValue == null) {
					((IrisObject)context.getRunningType()).getObjectClass().AddConstance(variableName, IrisValue.CloneValue(value));
				} else {
					/* Error */
                    throw new IrisConstanceReassignedException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                            IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Constance of" + variableName +
                            "has already assigned.");
				}
				break;
			}
		}
		return value;
	}

	protected static IrisValue GetGlobalVariable(String variableName, IrisThreadInfo threadInfo, IrisContextEnvironment context) {
		IrisValue value = null;
		value = IrisInterpreter.INSTANCE.GetGlobalValue(variableName);
		if(value == null) {
			value = IrisValue.CloneValue(IrisDevUtil.Nil());
			IrisInterpreter.INSTANCE.AddGlobalValue(variableName, value);
		}
		return value;
	}

	protected static IrisValue SetGlobalVariable(String variableName, IrisValue value, IrisThreadInfo threadInfo, IrisContextEnvironment context) {
		IrisValue testValue = IrisInterpreter.INSTANCE.GetGlobalValue(variableName);
		if(testValue == null) {
			IrisInterpreter.INSTANCE.AddGlobalValue(variableName, IrisValue.CloneValue(value));
		} else {
			testValue.setObject(value.getObject());
		}
		return value;
	}

	protected static IrisValue GetInstanceVariable(String variableName, IrisThreadInfo threadInfo, IrisContextEnvironment context) {
		IrisValue value = null;
		IrisObject obj = (IrisObject) context.getRunningType();

		if(obj != null) {
			value = obj.GetInstanceVariable(variableName);
			if(value == null) {
				value = IrisValue.CloneValue(IrisDevUtil.Nil());
				obj.AddInstanceVariable(variableName, value);
			}
			else {
				value = IrisValue.CloneValue(value);
			}
		} else {
			//value = context.GetLocalVariable(variableName);
            value= context.getClosureBlockObj() != null ?
                    ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).GetInstanceVariable(variableName)
                    : context.GetLocalVariable(variableName);
			if(value == null) {
				value = IrisValue.CloneValue(IrisDevUtil.Nil());
				//context.AddLocalVariable(variableName, IrisValue.CloneValue(value));
                if(context.getClosureBlockObj() != null) {
                    ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).AddLocalVariable(variableName, IrisValue.CloneValue(value));
                }
                else {
                    context.AddLocalVariable(variableName, IrisValue.CloneValue(value));
                }
			}
			else {
				value = IrisValue.CloneValue(value);
			}
		}

		return value;
	}

	protected static IrisValue SetInstanceVariable(String variableName, IrisValue value, IrisThreadInfo threadInfo, IrisContextEnvironment context) {

		IrisValue testValue = null;
		IrisObject obj = (IrisObject) context.getRunningType();

		if(obj != null) {
			testValue = obj.GetInstanceVariable(variableName);
			if(testValue == null) {
				obj.AddInstanceVariable(variableName, IrisValue.CloneValue(value));
			} else {
				testValue.setObject(value.getObject());
			}
		} else {
			//testValue = context.GetLocalVariable(variableName);
            testValue = context.getClosureBlockObj() != null ?
                    ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).GetInstanceVariable(variableName)
                    : context.GetLocalVariable(variableName);
			if(testValue == null) {
				//context.AddLocalVariable(variableName, IrisValue.CloneValue(value));
                if(context.getClosureBlockObj() != null) {
                    ((IrisClosureBlock)(IrisDevUtil.GetNativeObjectRef(context.getClosureBlockObj()))).AddLocalVariable(variableName, IrisValue.CloneValue(value));
                }
                else {
                    context.AddLocalVariable(variableName, IrisValue.CloneValue(value));
                }
            } else {
				testValue.setObject(value.getObject());
			}
		}

		return value;
	}

	protected static boolean CompareCounterLess(IrisValue org, IrisValue tar, IrisThreadInfo threadInfo, IrisContextEnvironment context) throws IrisExceptionBase {
		threadInfo.AddParameter(tar);
		IrisValue result = CallMethod(org, "<", threadInfo, context, 1);
		threadInfo.PopParameter(1);
		return result == IrisDevUtil.True();
	}

	protected static void DefineDefaultGetter(String methodName,
                                               String targetVariale,
                                               IrisMethod.MethodAuthority authority,
                                               IrisContextEnvironment context,
                                               IrisThreadInfo threadInfo) throws IrisExceptionBase {

        if(context.getRunTimeType() == RunTimeType.ClassDefineTime
                || context.getRunTimeType() == RunTimeType.ModuleDefineTime) {
            IrisClass classObj = (IrisClass)context.getRunningType();
            IrisMethod method = new IrisMethod(methodName, targetVariale, IrisMethod.GetterSetter.Getter, authority);
            classObj.AddInstanceMethod(method);
        }
        else {
            // Error
            throw new IrisAccessorDefinedException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                    IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Getter can only be defined in class " +
                            "or module");
        }
    }

    protected static void DefineDefaultSetter(String methodName,
                                              String targetVariale,
                                              IrisMethod.MethodAuthority authority,
                                              IrisContextEnvironment context,
                                              IrisThreadInfo threadInfo) throws IrisExceptionBase {
        if(context.getRunTimeType() == RunTimeType.ClassDefineTime) {
            IrisClass classObj = (IrisClass)context.getRunningType();
            IrisMethod method = new IrisMethod(methodName, targetVariale, IrisMethod.GetterSetter.Setter, authority);
            classObj.AddInstanceMethod(method);
        }
        else {
            // Error
            throw new IrisAccessorDefinedException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                    IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Setter can only be defined in class " +
                    "or module");
        }
    }
	protected static void DefineInstanceMethod(
	        Class<?> nativeClass,
			String nativeName,
			String methodName,
			String[] parameters,
			String variableParameter,
			String withBlockName,
			String withoutBlockName,
			IrisMethod.MethodAuthority authority,
			IrisContextEnvironment context,
			IrisThreadInfo threadInfo) throws IrisExceptionBase {

		IrisUserMethod userMethod = new IrisUserMethod();
		if(parameters != null) {
			userMethod.setParameterList(new ArrayList<String>(Arrays.asList(parameters)));
		} else {
			userMethod.setParameterList(null);
		}

		if(variableParameter != null) {
			userMethod.setVariableParameterName(variableParameter);
		}

		if(context.getRunTimeType() == IrisContextEnvironment.RunTimeType.RunTime) {
			IrisInterpreter.INSTANCE.AddMainMethod(
					new IrisMethod(
							methodName,
							userMethod,
							authority,
							IrisDevUtil.GetIrisNativeUserMethodHandle(nativeClass, nativeName)));
		} else if(context.getRunTimeType() == RunTimeType.ClassDefineTime) {
			IrisClass classObj = (IrisClass)context.getRunningType();
			classObj.AddInstanceMethod(nativeClass, nativeName, methodName, userMethod, authority);
		} else if(context.getRunTimeType() == RunTimeType.ModuleDefineTime) {
            IrisModule moduleObj = (IrisModule) context.getRunningType();
            moduleObj.AddInstanceMethod(nativeClass, nativeName, methodName, userMethod, authority);
        }
	}

    protected static void DefineClassMethod(
            Class<?> nativeClass,
            String nativeName,
            String methodName,
            String[] parameters,
            String variableParameter,
            String withBlockName,
            String withoutBlockName,
            IrisMethod.MethodAuthority authority,
            IrisContextEnvironment context,
            IrisThreadInfo threadInfo) throws IrisExceptionBase {
        IrisUserMethod userMethod = new IrisUserMethod();
        if (parameters != null) {
            userMethod.setParameterList(new ArrayList<String>(Arrays.asList(parameters)));
        } else {
            userMethod.setParameterList(null);
        }

        if (variableParameter != null) {
            userMethod.setVariableParameterName(variableParameter);
        }

        if (context.getRunTimeType() == IrisContextEnvironment.RunTimeType.RunTime
                || context.getRunTimeType() == RunTimeType.InterfaceDefineTime) {
            // Error
            throw new IrisMethodDefinedException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                    IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(), "Class method can only be defined in " +
                    " class or module");
        }
        else if(context.getRunTimeType() == IrisContextEnvironment.RunTimeType.ClassDefineTime) {
            IrisClass classObj = (IrisClass)context.getRunningType();
            classObj.AddClassMethod(nativeClass, nativeName, methodName, userMethod, authority);
        }
        else if(context.getRunTimeType() == RunTimeType.ModuleDefineTime) {
            IrisModule moduleObj = (IrisModule)context.getRunningType();
            moduleObj.AddClassMethod(nativeClass, nativeName, methodName, userMethod, authority);
        }
    }

	protected static IrisContextEnvironment DefineClass(String className, IrisContextEnvironment context,
														IrisThreadInfo threadInfo) {
		IrisContextEnvironment newEnv = new IrisContextEnvironment();
		newEnv.setRunTimeType(IrisContextEnvironment.RunTimeType.ClassDefineTime);
		newEnv.setUpperContext(context);

		// check if open class
		IrisModule upperModule = null;
		IrisContextEnvironment upperContext = context;
		while(upperContext != null) {
			upperModule = (IrisModule)upperContext.getRunningType();
			if(upperModule != null) {
				break;
			}
			upperContext = upperContext.getUpperContext();
		}

		IrisClass currentClass = null;
		IrisValue result = null;
		if(upperModule != null) {
			result = upperModule.GetConstance(className);
		}
		else {
			result = IrisInterpreter.INSTANCE.GetConstance(className);
		}

		if(result != null && IrisDevUtil.CheckClass(result, "Class")) {
			currentClass = ((IrisClassBase.IrisClassBaseTag)IrisDevUtil.GetNativeObjectRef(result)).getClassObj();
		}
		else {
            try {
                currentClass = new IrisClass(className, upperModule, IrisDevUtil.GetClass("Object"));
                if(upperModule != null) {
                    upperModule.AddConstance(className, IrisValue.WrapObject(currentClass.getClassObject()));
                    upperModule.AddSubClass(currentClass);
                }
                else {
                    IrisInterpreter.INSTANCE.AddConstance(className, IrisValue.WrapObject(currentClass.getClassObject()));
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        //
		newEnv.setRunningType(currentClass);

		return newEnv;
	}

	protected static IrisContextEnvironment DefineModule(String moduleName, IrisContextEnvironment context,
                                                         IrisThreadInfo threadInfo) {
        IrisContextEnvironment newEnv = new IrisContextEnvironment();
        newEnv.setRunTimeType(RunTimeType.ModuleDefineTime);
        newEnv.setUpperContext(context);

        // check if open module
        IrisModule upperModule = null;
        IrisContextEnvironment upperContext = context;
        while(upperContext != null) {
            upperModule = (IrisModule)upperContext.getRunningType();
            if(upperModule != null) {
                break;
            }
            upperContext = upperContext.getUpperContext();
        }

        IrisModule currentModule = null;
        IrisValue result = null;
        if(upperModule != null) {
            result = upperModule.SearchConstance(moduleName);
        }
        else {
            result = IrisInterpreter.INSTANCE.GetConstance(moduleName);
        }

        if(result != null && IrisDevUtil.CheckClass(result, "Module")) {
            currentModule = ((IrisModuleBase.IrisModuleBaseTag)IrisDevUtil.GetNativeObjectRef(result)).getModule();
        }
        else {
            try {
                currentModule = new IrisModule(moduleName, upperModule);
                if(upperModule != null) {
                    upperModule.AddConstance(moduleName, IrisValue.WrapObject(currentModule.getModuleObject()));
                    upperModule.AddSubModule(currentModule);
                }
                else {
                    IrisInterpreter.INSTANCE.AddConstance(moduleName, IrisValue.WrapObject(currentModule.getModuleObject()));
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        //
        newEnv.setRunningType(currentModule);

        return newEnv;
    }

	protected static void SetSuperClass(IrisContextEnvironment context,  IrisThreadInfo
            threadInfo) throws IrisExceptionBase {
	    IrisValue superClass = threadInfo.GetTempSuperClass();

	    if(context.getRunTimeType() != RunTimeType.ClassDefineTime) {
	        // Error
            throw new IrisUnkownFatalException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                    IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(),
                    "Oh, shit! An UNKNOWN ERROR has been lead to by YOU to Iris! What a SHIT unlucky man you are! " +
                            "Please don't approach Iris ANYMORE ! - Super class can not be set here.");
        }

        if(!IrisDevUtil.CheckClass(superClass, "Class")) {
	        // Error
            throw new IrisTypeNotCorretException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                    IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(),
                    "Super class must be a class.");
        }

        IrisClass classObj = (IrisClass) context.getRunningType();
	    classObj.setSuperClass(((IrisClassBase.IrisClassBaseTag)IrisDevUtil.GetNativeObjectRef(superClass)).getClassObj());
    }

    protected static void AddModule(IrisContextEnvironment context,  IrisThreadInfo
            threadInfo) throws IrisExceptionBase {
        LinkedList<IrisValue> tempModules = threadInfo.GetTempModules();

        for(IrisValue involvedModule : tempModules) {
            if(context.getRunTimeType() == RunTimeType.ClassDefineTime){
                IrisClass classObj = (IrisClass)context.getRunningType();
                if(!IrisDevUtil.CheckClass(involvedModule, "Module")) {
                    // Error
                    throw new IrisTypeNotCorretException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                            IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(),
                            "Only module can be involved.");
                }
                IrisModule tmpModuleObj = ((IrisModuleBase.IrisModuleBaseTag)IrisDevUtil.GetNativeObjectRef
                        (involvedModule)).getModule();
                classObj.AddInvolvedModule(tmpModuleObj);
            }
            else if(context.getRunTimeType() == RunTimeType.ModuleDefineTime) {
                IrisModule moduleObj = (IrisModule) context.getRunningType();
                if(!IrisDevUtil.CheckClass(involvedModule, "Module")) {
                    // Error
                    throw new IrisTypeNotCorretException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                            IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(),
                            "Only module can be involved.");
                }
                IrisModule tmpModuleObj = (IrisModule)IrisDevUtil.GetNativeObjectRef(involvedModule);
                moduleObj.AddInvolvedModule(tmpModuleObj);;
            }
            else {
                // Error
                throw new IrisUnkownFatalException(IrisDevUtil.GetCurrentThreadInfo().getCurrentFileName(),
                        IrisDevUtil.GetCurrentThreadInfo().getCurrentLineNumber(),
                        "Oh, shit! An UNKNOWN ERROR has been lead to by YOU to Iris! What a SHIT unlucky man you are! " +
                                "Please don't approach Iris ANYMORE ! - Module can not be involved here.");
            }
        }
    }

    protected static void AddInterface(IrisContextEnvironment context, IrisThreadInfo threadInfo) {

    }

    protected static void SetClassMethodAuthority(String methodName, IrisMethod.MethodAuthority authority,
                                                  IrisContextEnvironment environment, IrisThreadInfo threadInfo)
            throws IrisExceptionBase{
        switch (environment.getRunTimeType()) {
            case ClassDefineTime:
                ((IrisClass)environment.getRunningType()).SetClassMethodAuthority(methodName, authority);
                break;
            case ModuleDefineTime:
                ((IrisModule)environment.getRunningType()).SetClassMethodAuthority(methodName, authority);
                break;
            case InterfaceDefineTime:
                break;
            case RunTime:
                break;
        }
    }

    protected static void SetInstanceMethodAuthority(String methodName, IrisMethod.MethodAuthority authority,
                                                     IrisContextEnvironment environment, IrisThreadInfo threadInfo)
            throws IrisExceptionBase {
        switch (environment.getRunTimeType()) {
            case ClassDefineTime:
                ((IrisClass)environment.getRunningType()).SetInstanceMethodAuthority(methodName, authority);
                break;
            case ModuleDefineTime:
                ((IrisModule)environment.getRunningType()).SetInstanceMethodAuthority(methodName, authority);
                break;
            case InterfaceDefineTime:
                break;
            case RunTime:
                break;
        }
    }

    protected static IrisValue CreateClosureBlock(IrisContextEnvironment upperEnvironment, String[] parameters,
                                   String variableParameter, Class nativeMethodClass, String nativeMethodName,
                                   IrisThreadInfo threadInfo) {
        threadInfo.PushClosureBlock(
                new IrisClosureBlock(upperEnvironment, parameters != null ? new ArrayList<String>(Arrays.asList(parameters)) : null, variableParameter,
                        IrisDevUtil.GetIrisClosureBlockHandle(nativeMethodClass, nativeMethodName)
                )
        );
        return IrisValue.WrapObject(threadInfo.GetTopClosureBlock().getNativeObject());
    }

    protected static void ClearClosureBlock(IrisThreadInfo threadInfo) {
	    threadInfo.PopClosureBlock();
    }

    protected static IrisValue GetCastObject(IrisThreadInfo threadInfo) {
	    return threadInfo.GetTopClosureBlock() == null ? IrisDevUtil.Nil() : IrisValue.WrapObject(threadInfo.GetTopClosureBlock().getNativeObject());
    }

    protected static IrisValue GetSelfObject(IrisContextEnvironment context, IrisThreadInfo info) throws IrisExceptionBase {
        if(context.getClosureBlockObj() != null) {
            IrisContextEnvironment tmpEnv = context;
            while(tmpEnv != null) {
                if(tmpEnv.getRunningType() != null && tmpEnv.getRunTimeType() == RunTimeType.RunTime) {
                    return IrisValue.WrapObject((IrisObject)(context.getRunningType()));
                }
                tmpEnv = tmpEnv.getUpperContext();
            }
            throw new IrisWrongSelfException(info.getCurrentFileName(), info.getCurrentLineNumber(), "No object can be found with self.");
        }
        else {
            return IrisValue.WrapObject((IrisObject)(context.getRunningType()));
        }
    }
}
