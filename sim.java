import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Vector;


public class sim 
{
	private int numberOfCommands = 0;
	private int totalCycles = 0;
	private int tagGen = 0;
	private int oldestInstruction = 0;
	private String command = "";
	private BufferedReader br;
	
	private Vector<Instruction> dispatchList = new Vector<Instruction>();
	private Vector<Instruction> issueList = new Vector<Instruction>();
	private Vector<Instruction> executeList = new Vector<Instruction>();
	private Vector<Instruction> fakeROB = new Vector<Instruction>();
	private HashMap<Integer,String> notReadyRegisters = new HashMap<Integer,String>();
	private Vector<String> rawHazardList = new Vector<String>();
	
	private CacheSet[] L1;
	private CacheAdapter ca = new CacheAdapter();
	private int numberOfCacheSets = 0;
	private int L1size = 4096;
	private int L1assoc = 2;
	private int L1blocksize = 16;
	static boolean cacheEnabled = false;
	private int numberBlockOffsetBits;
	private int numberIndexBits;
	private int numberTagBits;
	private String cacheTag = "";
	private String cacheIndex = "";
	private String cacheBlockOffset = "";
	private Vector<Instruction> fetchingList = new Vector<Instruction>();
	private Vector<String> fetchingTags = new Vector<String>();
	private int stallIF = 0;
	static int missPenalty = 0;
	
	
	static String filename = "";
	static int schedulingQueueSize = 0;
	static int issueRate = 0;
	
	public sim()
	{
	try
		{
		if (cacheEnabled)
			{
			numberOfCacheSets = (L1size)/(L1assoc*L1blocksize);
			numberBlockOffsetBits = (int) (Math.log(L1blocksize)/Math.log(2));
			numberIndexBits = (int) (Math.log(numberOfCacheSets)/Math.log(2));
			numberTagBits = 32 - numberBlockOffsetBits - numberIndexBits;
			L1 = new CacheSet[numberOfCacheSets];
			for (int i = 0; i < numberOfCacheSets; i++)
			{
			L1[i] = new CacheSet();
			L1[i].setCount = 0;
			L1[i].index = ca.decimalToBinary(i);
			L1[i].tag = new String[L1assoc];
				for (int h = 0; h < L1assoc; h++)
					{
					L1[i].tag[h] = "";
					}
			while (L1[i].index.length() != (Math.log(numberOfCacheSets)/Math.log(2)))
				{
				if (numberOfCacheSets == 1)
					break;
				L1[i].index = "0" + L1[i].index;
				}
			L1[i].CacheBlock = new CacheBlock[L1assoc];
			for (int j = 0; j < L1assoc; j++)
				{
				L1[i].CacheBlock[j] = new CacheBlock();
				}
			}
			}
		br = new BufferedReader(new FileReader(filename));	// read trace
		command = br.readLine();
		try
			{
	while(true)
		{
		
			if (fakeROB.size() != 0) // fake retire
				{

				while (true)
					{
					Instruction instruction;
					if (!cacheEnabled)
						{
						instruction = fakeROB.firstElement();
						}
					else
						{
						if (command == null && fakeROB.size() == 0)
							{
							printStuff();
							return;
							}
						if  (fakeROB.size() == 0)
							break;
						int oldest = fakeROB.firstElement().tag;
						int oldestIndex = 0;
						for (int i = 0; i < fakeROB.size(); i++)
							{
							if (fakeROB.elementAt(i).tag < oldest)
								{
								oldest = fakeROB.elementAt(i).tag;
								oldestIndex = i;
								}
							}
						instruction = fakeROB.elementAt(oldestIndex);
						}
					if (instruction.stage.equals(InstructionTypes.WRITEBACK) && instruction.tag == oldestInstruction)
						{
						instruction.WBduration = 1;
						System.out.println(instruction.tag + " fu{" + instruction.type + "} src{" + 
								instruction.source1 + "," + instruction.source2 + "} dst{" + instruction.dest
									+ "} IF{" + instruction.IFstart + "," + instruction.IFduration + "} ID{"
									+ instruction.IDstart + "," + instruction.IDduration + "} IS{" + 
									instruction.ISstart + "," + instruction.ISduration + "} EX{" +
									instruction.EXstart + "," + instruction.EXduration + "} WB{" +
									instruction.WBstart + "," + instruction.WBduration + "}");
						if (totalCycles == 33)
							{
							int y = 1;
							y = y + 1;
							}
						fakeROB.remove(instruction);
						try
							{
						if (!cacheEnabled)
							oldestInstruction = fakeROB.firstElement().tag;
						else
							oldestInstruction++;
							}
						catch (NoSuchElementException nsee)
							{
							printStuff();
							return;
							}
						}
					else
						break;
					}
				}
			
			if (executeList.size() != 0)	// execute
				{
				Vector<Instruction> removeQueue = new Vector<Instruction>();
				for (int i = 0; i < executeList.size(); i++)
					{
					Instruction instruction = executeList.elementAt(i);
					//Instruction instruction = executeList.firstElement();
					if (instruction.EXcounter == 0)
						{
						instruction.stage = InstructionTypes.WRITEBACK;
						instruction.WBstart = totalCycles;
						instruction.EXduration = totalCycles - instruction.EXstart;
						notReadyRegisters.remove(instruction.tag);
						if (Integer.valueOf(instruction.dest) > 0)
							{
							rawHazardList.remove(instruction.tag + "," + instruction.dest);
							}
						//notReadyRegisters.remove(instruction.dest);
						removeQueue.add(instruction);
						//executeList.remove(instruction);
						}
					else
						instruction.EXcounter--;
					}
				for (Instruction instruction : removeQueue)
					{
					executeList.remove(instruction);
					}
				}
				
			if (issueList.size() != 0)	// issue
				{
				Vector<Instruction> removeQueue = new Vector<Instruction>();
				int issued = 0;
				for (int i = 0; i < issueList.size(); i++)
					{
					if (issued == issueRate)
						break;
					Instruction instruction = issueList.elementAt(i);
					if (instruction.tag == 1086)
					{
					int y = 1;
					y = y + 1;
					}
					/*if (notReadyRegisters.containsKey(instruction.rawHazardName))
						if (instruction.rawHazardReg.equals(notReadyRegisters.get(instruction.rawHazardName)))
							isReady = false;*/
					/*HashMap<Integer,String> unfinishedTasks = new HashMap<Integer,String>();
					Set<Integer> keys = notReadyRegisters.keySet();
					for (int key : keys)
						{
						if (key < instruction.tag)
							unfinishedTasks.put(key, notReadyRegisters.get(key));
						}*/
					if (!rawHazardList.contains(instruction.rawHazard1) && !rawHazardList.contains(instruction.rawHazard2) || instruction.tag == oldestInstruction)
						{
						instruction.ISduration = totalCycles - instruction.ISstart;
						instruction.stage = InstructionTypes.EXECUTE;
						issued++;
						instruction.EXstart = totalCycles;
						if (instruction.type == 0)
							instruction.EXcounter = 0;
						else if (instruction.type == 1)
							instruction.EXcounter = 1;
						else
							instruction.EXcounter = 4;
						executeList.add(instruction);
						removeQueue.add(instruction);
						//issueList.remove(instruction);
						}
					}
				for (Instruction instruction : removeQueue)
					{
					issueList.remove(instruction);
					}
				}
			
			if (dispatchList.size() != 0)		// dispatch
				{
				Vector<Instruction> removeQueue = new Vector<Instruction>();
				for (int i = 0; i < dispatchList.size(); i++)
					{
					Instruction instruction = dispatchList.elementAt(i);
					if (instruction.stage.equals(InstructionTypes.IDENTIFY))
						{
						if (issueList.size() < schedulingQueueSize)
							{
							instruction.IDduration = totalCycles - instruction.IDstart;
							instruction.ISstart = totalCycles;
							instruction.stage = InstructionTypes.ISSUE;
							//dispatchList.remove(instruction);
							removeQueue.add(instruction);
							issueList.add(instruction);
							}
						}
					else
						{
						instruction.stage = InstructionTypes.IDENTIFY;
						instruction.IFduration = totalCycles - instruction.IFstart;
						instruction.IDstart = totalCycles;
						}
					}
				for (Instruction instruction : removeQueue)
					{
					dispatchList.remove(instruction);
					}
				
				}
			
			int oldDispatchListSize = dispatchList.size();
				
			while (dispatchList.size() < 2*issueRate && command != null) // fetch
				{
				if (totalCycles == 302)
					{
					int y = 1;
					y = y + 1;
					}
				if (stallIF != 0)
					{
					stallIF--;
					break;
					}
				if (fetchingList.size() > 0)   // cache only
				{
				for (int i = 0; i < fetchingList.size(); i++)
					{
					if (totalCycles == fetchingList.elementAt(i).cacheFinishCycle)
						{
						Instruction insn = fetchingList.elementAt(i);
						insn.IFstart = totalCycles;
						insn.startCycle = totalCycles;
						setHazards(insn);
						fetchingList.remove(insn);
						fetchingTags.remove(insn.cacheTag + "," + insn.cacheIndex);
						}
					}
				if ((dispatchList.size() - oldDispatchListSize) == issueRate)
					break;
				if (dispatchList.size() == 2*issueRate)
					break;
				}
				
				Instruction instruction = new Instruction();
				int whitespace = command.indexOf(" ");
				if (cacheEnabled)
					{
					String address = "";
					address = command.substring(0,whitespace);
					BigInteger bi = new BigInteger(address, 16);
					String binaryCommand = bi.toString(2);
					if (binaryCommand.length() != 32)
						{
						for (int i = 32 - binaryCommand.length(); i > 0; i--)
							{
							binaryCommand = "0" + binaryCommand;
							}
						}
					cacheTag = binaryCommand.substring(0,numberTagBits);
					cacheTag = ca.binaryToHex(cacheTag);
					cacheIndex = binaryCommand.substring(numberTagBits,numberTagBits+numberIndexBits);
					if (cacheIndex.length() == 0)
						cacheIndex = "0";
					cacheBlockOffset = binaryCommand.substring(numberTagBits+numberIndexBits);
					instruction.cacheTag = cacheTag;
					instruction.cacheIndex = cacheIndex;
					}
				command = command.substring(1+whitespace);
				whitespace = command.indexOf(" ");
				instruction.type = Integer.valueOf(command.substring(0,whitespace));
				instruction.tag = tagGen;
				tagGen++;
				instruction.stage = InstructionTypes.FETCH;
				if (!cacheEnabled)
					{
					instruction.IFstart = totalCycles;
					instruction.startCycle = totalCycles;
					}
				command = command.substring(1+whitespace);
				whitespace = command.indexOf(" ");
				instruction.dest = command.substring(0,whitespace);
				command = command.substring(1+whitespace);
				whitespace = command.indexOf(" ");
				instruction.source1 = command.substring(0,whitespace);
				command = command.substring(1+whitespace);
				instruction.source2 = command.substring(0);
				if (instruction.tag == 1079)
				{
				int y = 1;
				y = y + 1;
				}
				
				if (!cacheEnabled)
				{
				setHazards(instruction);
				}
				
				if (cacheEnabled)		// cache only
					{
					if (!ca.checkCacheForTag(cacheTag, cacheIndex, L1assoc, L1))
						{
						instruction.cacheFinishCycle = totalCycles + missPenalty;
						fetchingList.add(instruction);
						stallIF = missPenalty;
						fetchingTags.add(cacheTag + "," + cacheIndex);
						ca.storeInCache(cacheTag, cacheIndex, cacheBlockOffset, L1assoc, L1);
						}
					else
						{
						instruction.IFstart = totalCycles;
						instruction.startCycle = totalCycles;
						setHazards(instruction);
						}
					}

				command = br.readLine();
				numberOfCommands++;
				if ((dispatchList.size() - oldDispatchListSize) == issueRate)
					break;
				}
			
	
				totalCycles++;

				}
			}
		
		catch (IOException ioe)
			{
			System.out.println(ioe.getMessage());
			}
		}
	
	catch (FileNotFoundException fnfe)
		{
		System.out.println("Trace file not found.");
		return;
		}
	catch (IOException ioe)
		{
		System.out.println(ioe.getMessage());
		}
	}


public static void main(String[] args) 		// obtain variables from console
	{
	if (args.length < 3 || args.length > 4)
		{
		System.out.println("Invalid number of command line arguments.");
		return;
		}
	schedulingQueueSize = Integer.valueOf(args[0]);
	issueRate = Integer.valueOf(args[1]);
	filename = args[2];
	if (args.length == 4)
		{
		cacheEnabled = true;
		missPenalty = Integer.valueOf(args[3]);
		}
		
	new sim();	// instantiate sim object
	}

	void setHazards(Instruction instruction)
	{
	boolean hazard1 = false;
	boolean hazard2 = false;
	if (notReadyRegisters.containsValue(instruction.source1))
		hazard1 = true;
	if (notReadyRegisters.containsValue(instruction.source2))
		hazard2 = true;

	if (hazard1)
		{
		Vector<Integer> checkQueue = new Vector<Integer>();
		Set<Integer> keys = notReadyRegisters.keySet();
		for (int key : keys)				// get all entries in the NRR that could be RAW hazards
			{
			if (notReadyRegisters.get(key).equals(instruction.source1))
				{
				checkQueue.add(key);
				}
			}
		int proximity = instruction.tag - checkQueue.firstElement();
		int closestDependency = checkQueue.firstElement();
		for (int i = 1; i < checkQueue.size(); i++)
			{
			if (proximity > instruction.tag - checkQueue.elementAt(i))
				{
				proximity = instruction.tag - checkQueue.elementAt(i);
				closestDependency = checkQueue.elementAt(i);
				}
			}
		instruction.rawHazard1 = closestDependency + "," + notReadyRegisters.get(closestDependency);
		}

	if (hazard2)
		{
		Vector<Integer> checkQueue = new Vector<Integer>();
		Set<Integer> keys = notReadyRegisters.keySet();
		for (int key : keys)				// get all entries in the NRR that could be RAW hazards
			{
			if (notReadyRegisters.get(key).equals(instruction.source2))
				{
				checkQueue.add(key);
				}
			}
		int proximity = instruction.tag - checkQueue.firstElement();
		int closestDependency = checkQueue.firstElement();
		for (int i = 1; i < checkQueue.size(); i++)
			{
			if (proximity > instruction.tag - checkQueue.elementAt(i))
				{
				proximity = instruction.tag - checkQueue.elementAt(i);
				closestDependency = checkQueue.elementAt(i);
				}
			}
		instruction.rawHazard2 = closestDependency + "," + notReadyRegisters.get(closestDependency);
		}

	if (Integer.valueOf(instruction.dest) > 0)
		{
		if (notReadyRegisters.containsValue(instruction.dest))
			{
			Vector<Integer> removeQueue = new Vector<Integer>();
			Set<Integer> keys = notReadyRegisters.keySet();
				for (int key : keys)
					{
					if (notReadyRegisters.get(key).equals(instruction.dest))
						{
						removeQueue.add(key);
						}
					}
				for (int key : removeQueue)
					{
					notReadyRegisters.remove(key);
					}
			}
		notReadyRegisters.put(instruction.tag,instruction.dest);
		rawHazardList.add(instruction.tag + "," + instruction.dest);
		}
	dispatchList.add(instruction);
	fakeROB.add(instruction);
	}
	
void printStuff() throws IOException
	{
	br.close();
	System.out.println("CONFIGURATION");
	System.out.println("superscalar bandwidth (N) = " + issueRate);
	int dispatchQueueSize = 2*issueRate;
	System.out.println("dispatch queue size (2*N) = " + dispatchQueueSize);
	System.out.println("schedule queue size (S)   = " + schedulingQueueSize);
	if (cacheEnabled)
		System.out.println("i-cache miss penalty (K)    = " + missPenalty);
	System.out.println("RESULTS");
	System.out.println("number of instructions = " + numberOfCommands);
	System.out.println("number of cycles 	= " + totalCycles);
	Integer commandsI = new Integer(numberOfCommands);
	Integer cyclesI = new Integer(totalCycles);
	double commandsD = commandsI.doubleValue();
	double cyclesD = cyclesI.doubleValue();
	double ipcD = (commandsD/cyclesD);
	BigDecimal IPC = new BigDecimal(ipcD,MathContext.DECIMAL32);
	IPC = IPC.setScale(2,BigDecimal.ROUND_HALF_DOWN);
	System.out.println("IPC			= " + IPC);
	if (cacheEnabled)
	{
	String line = "";
	System.out.println(" ===== L1 contents =====");
	for (int i = 0; i < numberOfCacheSets; i++)
	{
	line = "set	" + i + ":	";
		for (int j = 0; j < L1assoc; j++)
			{
			if (L1[i].tag[j].length() > 0)
				line = line + L1[i].tag[j];
			else
				line = line + " - ";
				line = line + "     ";
			}
	System.out.println(line);
	}
	}
	return;
	}
	
}
