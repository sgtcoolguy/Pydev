/*
 * Created on May 24, 2005
 *
 * @author Fabio Zadrozny
 */
package org.python.pydev.editor.codecompletion.revisited;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.python.pydev.core.IModule;
import org.python.pydev.core.IModulesManager;
import org.python.pydev.core.IPythonNature;
import org.python.pydev.core.IToken;
import org.python.pydev.core.ModulesKey;
import org.python.pydev.core.REF;
import org.python.pydev.editor.codecompletion.revisited.modules.AbstractModule;
import org.python.pydev.editor.codecompletion.revisited.modules.CompiledModule;
import org.python.pydev.editor.codecompletion.revisited.modules.EmptyModule;
import org.python.pydev.editor.codecompletion.revisited.modules.SourceModule;

/**
 * This class manages the modules that are available
 * 
 * @author Fabio Zadrozny
 */
public abstract class ModulesManager implements IModulesManager, Serializable {
    
    private final static boolean DEBUG_BUILD = false;

    public ModulesManager(){
	}
	

	/**
	 * This class is a cache to help in getting the managers that are referenced or referred.
	 * 
	 * It will not actually make any computations (the managers must be set from the outside)
	 */
	protected static class CompletionCache{
		public ModulesManager[] referencedManagers;
		public ModulesManager[] referredManagers;
		public ModulesManager[] getManagers(boolean referenced) {
    		if(referenced){
				return this.referencedManagers;
    		}else{
				return this.referredManagers;
    		}
		}
		public void setManagers(ModulesManager[] ret, boolean referenced) {
			if(referenced){
				this.referencedManagers = ret;
			}else{
				this.referredManagers = ret;
			}
		}
	}
	
	/**
	 * A stack for keeping the completion cache
	 */
	protected transient volatile CompletionCache completionCache = null;
	private transient volatile int completionCacheI=0;
	
	
	/**
	 * This method starts a new cache for this manager, so that needed info is kept while the request is happening
	 * (so, some info may not need to be re-asked over and over for requests) 
	 */
	public synchronized boolean startCompletionCache(){
		if(completionCache == null){
			completionCache = new CompletionCache();
		}
		completionCacheI += 1;
		return true;
	}
	
	public synchronized void endCompletionCache(){
		completionCacheI -= 1;
		if(completionCacheI == 0){
			completionCache = null;
		}else if(completionCacheI < 0){
			throw new RuntimeException("Completion cache negative (request unsynched)");
		}
	}
	
    /**
     * Modules that we have in memory. This is persisted when saved.
     * 
     * Keys are ModulesKey with the name of the module. Values are AbstractModule objects.
     * 
     * Implementation changed to contain a cache, so that it does not grow to much (some out of memo errors
     * were thrown because of the may size when having too many modules).
     * 
     * It is sorted so that we can get things in a 'subtree' faster
     */
	protected transient SortedMap<ModulesKey, ModulesKey> modulesKeys = new TreeMap<ModulesKey, ModulesKey>();
	protected static transient ModulesManagerCache cache = createCache();
    
	private static ModulesManagerCache createCache(){
		return new ModulesManagerCache();
	}
	
    /**
     * This is the set of files that was found just right after unpickle (it should not be changed after that,
     * and serves only as a reference cache).
     */
    protected transient Set<File> files = new HashSet<File>();

    /**
     * Helper for using the pythonpath. Also persisted.
     */
    protected PythonPathHelper pythonPathHelper = new PythonPathHelper();
    
    public PythonPathHelper getPythonPathHelper(){
        return pythonPathHelper;
    }

    /**
     * The version for deserialization
     */
    private static final long serialVersionUID = 2L;
    
    /**
     * Custom deserialization is needed.
     */
    private void readObject(ObjectInputStream aStream) throws IOException, ClassNotFoundException {
    	modulesKeys = new TreeMap<ModulesKey, ModulesKey>();
    	
        files = new HashSet<File>();
        aStream.defaultReadObject();
        Set set = (Set) aStream.readObject();
        for (Iterator iter = set.iterator(); iter.hasNext();) {
            ModulesKey key = (ModulesKey) iter.next();
            //restore with empty modules.
            modulesKeys.put(key, key);
            if(key.file != null){
            	files.add(key.file);
            }
        }
    }

    
    /**
     * Custom serialization is needed.
     */
    private void writeObject(ObjectOutputStream aStream) throws IOException {
    	synchronized (modulesKeys) {
	        aStream.defaultWriteObject();
	        //write only the keys
	        aStream.writeObject(new HashSet<ModulesKey>(modulesKeys.keySet()));
    	}
    }

    /**
     * @param modules The modules to set.
     */
    private void setModules(SortedMap<ModulesKey, ModulesKey> keys) {
        this.modulesKeys = keys;
    }
    

    /**
     * @return Returns the modules.
     */
    protected Map<ModulesKey, AbstractModule> getModules() {
        throw new RuntimeException("Deprecated");
    }

    /**
     * Must be overriden so that the available builtins (forced or not) are returned.
     * @param defaultSelectedInterpreter 
     */
    public abstract String[] getBuiltins(String defaultSelectedInterpreter);

	/**
	 * 
	 * @param monitor this is the monitor
	 * @param pythonpathList this is the pythonpath
	 * @param completions OUT - the files that were gotten as valid python modules 
	 * @param fromJar OUT - the names of the modules that were found inside a jar
	 * @return the total number of modules found (that's completions + fromJar)
	 */
	private int listFilesForCompletion(IProgressMonitor monitor, List<String> pythonpathList, List<File> completions, List<String> fromJar) {
		int total = 0;
		//first thing: get all files available from the python path and sum them up.
        for (Iterator iter = pythonpathList.iterator(); iter.hasNext() && monitor.isCanceled() == false;) {
            String element = (String) iter.next();

            //the slow part is getting the files... not much we can do (I think).
            File root = new File(element);
            List<File>[] below = pythonPathHelper.getModulesBelow(root, monitor);
            if(below != null){
                completions.addAll(below[0]);
                total += below[0].size();
                
            }else{ //ok, it was null, so, maybe this is not a folder, but  zip file with java classes...
                List<String> currFromJar = PythonPathHelper.getFromJar(root, monitor);
                if(currFromJar != null){
                    fromJar.addAll(currFromJar);
                    total += currFromJar.size();
                }
            }
        }
		return total;
	}

	public void changePythonPath(String pythonpath, final IProject project, IProgressMonitor monitor, String defaultSelectedInterpreter) {
		List<String> pythonpathList = pythonPathHelper.setPythonPath(pythonpath);
		List<File> completions = new ArrayList<File>();
		List<String> fromJar = new ArrayList<String>();
		int total = listFilesForCompletion(monitor, pythonpathList, completions, fromJar);
		changePythonPath(pythonpath, project, monitor, pythonpathList, completions, fromJar, total, defaultSelectedInterpreter);
	}
	
    /**
     * @param pythonpath string with the new pythonpath (separated by |)
     * @param project may be null if there is no associated project.
     */
    private void changePythonPath(String pythonpath, final IProject project, IProgressMonitor monitor, 
    		List<String> pythonpathList, List<File> completions, List<String> fromJar, int total, String defaultSelectedInterpreter) {

    	SortedMap<ModulesKey, ModulesKey> keys = new TreeMap<ModulesKey, ModulesKey>();
        int j = 0;

        //now, create in memory modules for all the loaded files (empty modules).
        for (Iterator iterator = completions.iterator(); iterator.hasNext() && monitor.isCanceled() == false; j++) {
            Object o = iterator.next();
            if (o instanceof File) {
                File f = (File) o;
                String fileAbsolutePath = REF.getFileAbsolutePath(f);
                String m = pythonPathHelper.resolveModule(fileAbsolutePath);

                monitor.setTaskName(new StringBuffer("Module resolved: ").append(j).append(" of ").append(total).append(" (").append(m)
                        .append(")").toString());
                monitor.worked(1);
                if (m != null) {
                    //we don't load them at this time.
                    ModulesKey modulesKey = new ModulesKey(m, f);
                    
                    //ok, now, let's resolve any conflicts that we might find...
                    boolean add = false;
                    
                    //no conflict (easy)
                    if(!keys.containsKey(modulesKey)){
                        add = true;
                    }else{
                        //we have a conflict, so, let's resolve which one to keep (the old one or this one)
                        if(PythonPathHelper.isValidSourceFile(fileAbsolutePath)){
                            //source files have priority over other modules (dlls) -- if both are source, there is no real way to resolve
                            //this priority, so, let's just add it over.
                            add = true;
                        }
                    }
                    
                    if(add){
                    	//the previous must be removed (if there was any)
                    	keys.remove(modulesKey);
                    	keys.put(modulesKey, modulesKey);
                    }
                }
            }
        }
        
        for (String modName : fromJar) {
        	final ModulesKey k = new ModulesKey(modName, null);
			keys.put(k, k);
        }

        onChangePythonpath(defaultSelectedInterpreter, keys);
        
        //assign to instance variable
        this.setModules(keys);

    }

    /**
     * Subclasses may do more things after the defaults were added to the cache (e.g.: the system modules manager may
     * add builtins)
     */
    protected void onChangePythonpath(String defaultSelectedInterpreter, SortedMap<ModulesKey, ModulesKey> keys) {
    }


    /**
     * This is the only method that should remove a module.
     * No other method should remove them directly.
     * 
     * @param key this is the key that should be removed
     */
    protected void doRemoveSingleModule(ModulesKey key) {
    	synchronized (modulesKeys) {
    	    if(DEBUG_BUILD){
    	        System.out.println("Removing module:"+key+" - "+this.getClass());
    	    }
	        this.modulesKeys.remove(key);
	        ModulesManager.cache.remove(key, this);
    	}
    }

    /**
     * This is the only method that should add / update a module.
     * No other method should add it directly (unless it is loading or rebuilding it).
     * 
     * @param key this is the key that should be added
     * @param n 
     */
    public void doAddSingleModule(final ModulesKey key, AbstractModule n) {
        if(DEBUG_BUILD){
            System.out.println("Adding module:"+key+" - "+this.getClass());
        }
    	synchronized (modulesKeys) {
	    	this.modulesKeys.put(key, key);
	        ModulesManager.cache.add(key, n, this);
    	}
    }

    /**
     * @return a set of all module keys
     */
    public Set<String> getAllModuleNames() {
        Set<String> s = new HashSet<String>();
    	synchronized (modulesKeys) {
	        for (ModulesKey key : this.modulesKeys.keySet()) {
	            s.add(key.name);
	        }
    	}
        return s;
    }

    public SortedMap<ModulesKey,ModulesKey> getAllDirectModulesStartingWith(String strStartingWith) {
    	if(strStartingWith.length() == 0){
    		return modulesKeys;
    	}
    	ModulesKey startingWith = new ModulesKey(strStartingWith, null);
    	ModulesKey endingWith = new ModulesKey(startingWith+"z", null);
    	synchronized (modulesKeys) {
	    	//we don't want it to be backed up by the same set (because it may be changed, so, we may get
	    	//a java.util.ConcurrentModificationException on places that use it)
	    	return new TreeMap<ModulesKey, ModulesKey>(modulesKeys.subMap(startingWith, endingWith));
    	}
    }
    
    public SortedMap<ModulesKey,ModulesKey> getAllModulesStartingWith(String strStartingWith) {
    	return getAllDirectModulesStartingWith(strStartingWith);
    }
    
    public ModulesKey[] getOnlyDirectModules() {
    	synchronized (modulesKeys) {
    		return (ModulesKey[]) this.modulesKeys.keySet().toArray(new ModulesKey[0]);
    	}
    }

    /**
     * @return
     */
    public int getSize() {
        return this.modulesKeys.size();
    }
    


    public IModule getModule(String name, IPythonNature nature, boolean dontSearchInit) {
        return getModule(true, name, nature, dontSearchInit);
    }
    
    /**
     * This method returns the module that corresponds to the path passed as a parameter.
     * 
     * @param name the name of the module we're looking for  (e.g.: mod1.mod2)
     * @param dontSearchInit is used in a negative form because initially it was isLookingForRelative, but
     * it actually defines if we should look in __init__ modules too, so, the name matches the old signature.
     * 
     * NOTE: isLookingForRelative description was: when looking for relative imports, we don't check for __init__
     * @return the module represented by this name
     */
    public IModule getModule(boolean acceptCompiledModule, String name, IPythonNature nature, boolean dontSearchInit) {
        AbstractModule n = null;
        ModulesKey keyForCacheAccess = new ModulesKey(null, null);
        
        if(!dontSearchInit){
            if(n == null){
            	keyForCacheAccess.name = new StringBuffer(name).append(".__init__").toString();
                n = cache.getObj(keyForCacheAccess, this);
                if(n != null){
                    name += ".__init__";
                }
            }
        }
        if (n == null) {
        	keyForCacheAccess.name = name;
        	n = cache.getObj(keyForCacheAccess, this);
        }

        if (n instanceof SourceModule) {
            //ok, module exists, let's check if it is synched with the filesystem version...
            SourceModule s = (SourceModule) n;
            if (!s.isSynched()) {
                //change it for an empty and proceed as usual.
                n = new EmptyModule(s.getName(), s.getFile());
                doAddSingleModule(new ModulesKey(s.getName(), s.getFile()), n);
            }
        }

        if (n instanceof EmptyModule) {
            EmptyModule e = (EmptyModule) n;

            //let's treat os as a special extension, since many things it has are too much
            //system dependent, and being so, many of its useful completions are not goten
            //e.g. os.path is defined correctly only on runtime.

            boolean found = false;

            if (!found && e.f != null) {
            	if(!e.f.exists()){
            		keyForCacheAccess.name = name;
            		keyForCacheAccess.file = e.f;
            		doRemoveSingleModule(keyForCacheAccess);
            		n = null;
            	}else{
	                try {
	                    n = AbstractModule.createModule(name, e.f, nature, -1);
	                } catch (IOException exc) {
	                	keyForCacheAccess.name = name;
	                	keyForCacheAccess.file = e.f;
	                    doRemoveSingleModule(keyForCacheAccess);
	                    n = null;
	                }
                }

            }else{ //ok, it does not have a file associated, so, we treat it as a builtin (this can happen in java jars)
                if(acceptCompiledModule){
                    n = new CompiledModule(name, IToken.TYPE_BUILTIN, nature.getAstManager());
                }else{
                    return null;
                }
            }
            
            if (n != null) {
                doAddSingleModule(new ModulesKey(name, e.f), n);
            } else {
                System.err.println("The module " + name + " could not be found nor created!");
            }
        }

        if (n instanceof EmptyModule) {
            throw new RuntimeException("Should not be an empty module anymore!");
        }
        return n;
    }
    
    protected AbstractModule getBuiltinModule(String name, IPythonNature nature, boolean dontSearchInit) {
        //default implementation returns null (only the SystemModulesManager will actually return something here)
        return null;
    }

    
    
    /**
     * Passes through all the compiled modules in memory and clears its tokens (so that
     * we restore them when needed).
     */
    public void clearCache(){
        ModulesManager.cache.internalCache.clear();
    }


    /** 
     * @see org.python.pydev.core.IProjectModulesManager#isInPythonPath(org.eclipse.core.resources.IResource, org.eclipse.core.resources.IProject)
     */
    public boolean isInPythonPath(IResource member, IProject container) {
        return resolveModule(member, container) != null;
    }

    
    /** 
     * @see org.python.pydev.core.IProjectModulesManager#resolveModule(org.eclipse.core.resources.IResource, org.eclipse.core.resources.IProject)
     */
    public String resolveModule(IResource member, IProject container) {
        File inOs = member.getRawLocation().toFile();
        return resolveModule(REF.getFileAbsolutePath(inOs));
    }

    protected String getResolveModuleErr(IResource member) {
		return "Unable to find the path "+member+" in the project were it\n" +
        "is added as a source folder for pydev."+this.getClass();
	}

	/**
     * @param full
     * @return
     */
    public String resolveModule(String full) {
        return pythonPathHelper.resolveModule(full, false);
    }

    public List<String> getPythonPath(){
        return pythonPathHelper.getPythonpath();
    }

}