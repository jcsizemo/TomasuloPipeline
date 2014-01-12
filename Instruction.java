
public class Instruction 
{
String stage = "";
int cycles = 0;
int startCycle = 0;
boolean isReady = true;
int type = 0;
int tag = 0;
int IFduration = 0;
int IDduration = 0;
int ISduration = 0;
int EXduration = 0;
int WBduration = 0;
int IFstart = 0;
int IDstart = 0;
int ISstart = 0;
int EXstart = 0;
int WBstart = 0;
String source1 = "";
String source2 = "";
String dest = "";
int EXcounter = 0;
int rawHazardName = 0;
String rawHazard1 = "";
String rawHazard2 = "";
int cacheFinishCycle = 0;
String cacheTag = "";
String cacheIndex = "";
}
