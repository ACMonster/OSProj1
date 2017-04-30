package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.util.ArrayList;

import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
        pid = pidCounter++;

        processList.add(new ProcessInfo(-1, false, false));

        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];

        openFiles[0] = UserKernel.console.openForReading();
        openFiles[1] = UserKernel.console.openForWriting();
    }
    
    public UserProcess(int parentID) {
        pid = pidCounter++;

        processList.add(new ProcessInfo(parentID, false, false));

        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];

        openFiles[0] = UserKernel.console.openForReading();
        openFiles[1] = UserKernel.console.openForWriting();
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    private int[] addrTranslate(int vaddr, boolean writing) {
        // calculate virtual page number and offset from the virtual address
        int vpn = Processor.pageFromAddress(vaddr);
        int offset = Processor.offsetFromAddress(vaddr);

        TranslationEntry entry = null;
        TranslationEntry translations[] = pageTable;

        int paddr[] = new int[2];
        paddr[0] = -1;

        // if not using a TLB, then the vpn is an index into the table
        if (translations == null || vpn >= translations.length ||
        translations[vpn] == null ||
        !translations[vpn].valid) {
            return paddr;
        }

        entry = translations[vpn];

        // check if trying to write a read-only page
        if (entry.readOnly && writing) {
            return paddr;
        }

        // check if physical page number is out of range
        int ppn = entry.ppn;
        if (ppn < 0 || ppn >= Machine.processor().getNumPhysPages()) {
            return paddr;
        }

        // set used and dirty bits as appropriate
        entry.used = true;
        if (writing)
            entry.dirty = true;

        paddr[0] = 0;
        paddr[1] = ppn * pageSize + offset;

        return paddr;
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
    for (int i = 0; i < length; i++) {
        int paddr[] = addrTranslate(vaddr + i, false);
        if (paddr[0] == -1)
            return i;
        data[offset + i] = memory[paddr[1]];
    }

	return length;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
    Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

    byte[] memory = Machine.processor().getMemory();
    
    for (int i = 0; i < length; i++) {
        int paddr[] = addrTranslate(vaddr + i, true);
        if (paddr[0] == -1)
            return i;
        memory[paddr[1]] = data[offset + i];
    }

    return length;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;
    
	if (!loadSections()) {
        coff.close();
	    return false;
    }

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

    coff.close();
	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        boolean intStatus = Machine.interrupt().disable();
	if (numPages > UserKernel.freePages.size()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
        Machine.interrupt().restore(intStatus);
	    return false;
	}

    for (int s=0; s<coff.getNumSections(); s++) {
        CoffSection section = coff.getSection(s);
        int minvpn = section.getFirstVPN();
        int maxvpn = minvpn + section.getLength();
        if (minvpn < 0 || maxvpn > Machine.processor().getNumPhysPages()) {
            Machine.interrupt().restore(intStatus);
            return false;
        }
    }

	// load sections
	for (int s = 0; s < coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
        boolean readOnly = section.isReadOnly();

	    Lib.debug(dbgProcess, "\tinitializing " + section.getName()
		      + " section (" + section.getLength() + " pages)");

	    for (int i = 0; i < section.getLength(); i++) {
    		int vpn = section.getFirstVPN() + i;
            int ppn = UserKernel.freePages.removeFirst();

            pageTable[vpn] = new TranslationEntry(vpn, ppn, true, readOnly, false, false);

    		section.loadPage(i, ppn);
	    }
	}

    for (int i = numPages - stackPages - 1; i < numPages; i++) {
        int ppn = UserKernel.freePages.removeFirst();
        pageTable[i] = new TranslationEntry(i, ppn, true, false, false, false);
    }
    
    Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {

    if (this != UserKernel.currentProcess()) {
        System.out.println("Halt declined.");
        return -1;
    }


	Machine.halt();
	
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }

    private int handleOpen(int name, boolean creat) {
        String s = readVirtualMemoryString(name, maxFileNameLength);

        if (s != null) {
            OpenFile f = ThreadedKernel.fileSystem.open(s, creat);
            if (f != null) {
                int p = 0;
                while (p < maxFileNum && openFiles[p] != null)
                    p++;
                if (p == maxFileNum)
                    return -1;
                openFiles[p] = f;
                return p;
            }
        }

        return -1;
    }

    private int handleRead(int fd, int buffer, int size) {
        if (openFiles[fd] == null)
            return -1;

        byte buf[] = new byte[size];

        int cnt = openFiles[fd].read(buf, 0, size);

        if (cnt == -1)
            return -1;

        return writeVirtualMemory(buffer, buf, 0, cnt);
    }

    private int handleWrite(int fd, int buffer, int size) {
        if (openFiles[fd] == null || size > Machine.processor().getMemory().length)
            return -1;

        byte buf[] = new byte[size];

        readVirtualMemory(buffer, buf, 0, size);

        return openFiles[fd].write(buf, 0, size);
    }

    private int handleClose(int fd) {

        if (openFiles[fd] == null)
            return -1;

        openFiles[fd].close();

        return 0;
    }

    private int handleUnlink(int name) {
        String s = readVirtualMemoryString(name, maxFileNameLength);

        if (s != null)
            if (ThreadedKernel.fileSystem.remove(s))
                return 0;

        return -1;
    }

    private int handleExec(int file, int argc, int argv) {
        String name = readVirtualMemoryString(file, maxFileNameLength);

        if (name == null)
            return -1;


        String args[] = new String[argc];

        int cur = argv;
        for (int i = 0; i < argc; i++) {
            byte buf[] = new byte[4];
            readVirtualMemory(cur, buf, 0, 4);
            int value = Lib.bytesToInt(buf, 0);
            args[i] = readVirtualMemoryString(value, maxFileNameLength);
            cur += 4;
        }

        UserProcess proc = new UserProcess(pid);
        if (!proc.load(name, args))
            return -1;
        
        new UThread(proc).setName(name).fork();

        return proc.pid;
    }

    private int handleExit(int status, boolean normal) {
        /* close files */
        for (int i = 0; i < maxFileNum; i++)
            if (openFiles[i] != null)
                openFiles[i].close();

        /* release memory */
        for (int i = 0; i < numPages; i++) {
            TranslationEntry entry = pageTable[i];
            if (entry == null)
                continue;
            UserKernel.freePages.add(entry.ppn);
        }

        System.out.println("Exit: " + pid + " " + status);

        processList.get(pid).status = status;
        processList.get(pid).normal = normal;
        processList.get(pid).lock.V();

        KThread.currentThread().finish();

        return 0;
    }

    private int handleJoin(int processID, int status) {
        if (processID < 0 || processID >= pidCounter)
            return -1;
        if (processList.get(processID).parentID != pid)
            return -1;

        processList.get(processID).lock.P();

        System.out.println("Join: " + processID + " " + processList.get(processID).status);

        byte buf[] = Lib.bytesFromInt(processList.get(processID).status);

        if (writeVirtualMemory(status, buf, 0, 4) < 4) {
            //kill
        }

        if (processList.get(processID).normal)
            return 1;
        else
            return 0;
    }

    private static final int
        syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
    	switch (syscall) {
            case syscallHalt:
        	    return handleHalt();
            case syscallCreate:
                return handleOpen(a0, true);
            case syscallOpen:
                return handleOpen(a0, false);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlink(a0);
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallExit:
                return handleExit(a0, true);
            case syscallJoin:
                return handleJoin(a0, a1);
        	default:
        	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
        	    Lib.assertNotReached("Unknown system call!");
    	}
        return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
        handleExit(-1, false);
        break;
	    //Lib.debug(dbgProcess, "Unexpected exception: " +
		      //Processor.exceptionNames[cause]);
	    //Lib.assertNotReached("Unexpected exception");
	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    private static final int maxFileNameLength = 256;

    private static final int maxFileNum = 32;

    private OpenFile openFiles[] = new OpenFile[maxFileNum];

    private int pid;
    private static int pidCounter = 0;
    private static ArrayList<ProcessInfo> processList = new ArrayList<>();

    class ProcessInfo {
        int parentID;
        Semaphore lock;
        int status;
        boolean normal;

        ProcessInfo(int parentID, boolean finished, boolean normal) {
            this.parentID = parentID;
            this.lock = new Semaphore(0);
            this.normal = normal;
        }
    }
}
