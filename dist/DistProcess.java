/*
Copyright
All materials provided to the students as part of this course is the property of respective authors. Publishing them to third-party (including websites) is prohibited. Students may save it for their personal use, indefinitely, including personal cloud storage spaces. Further, no assessments published as part of this course may be shared with anyone else. Violators of this copyright infringement may face legal actions in addition to the University disciplinary proceedings.
©2022, Joseph D’Silva
*/
import java.io.*;

import java.util.*;
import java.nio.charset.StandardCharsets;

// To get the name of the host.
import java.net.*;

//To get the process id.
import java.lang.management.*;

import org.apache.zookeeper.*;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.KeeperException.*;
import org.apache.zookeeper.data.*;
import org.apache.zookeeper.KeeperException.Code;

// TODO
// Replace XX with your group number.
// You may have to add other interfaces such as for threading, etc., as needed.
// This class will contain the logic for both your master process as well as the worker processes.
//  Make sure that the callbacks and watch do not conflict between your master's logic and worker's logic.
//		This is important as both the master and worker may need same kind of callbacks and could result
//			with the same callback functions.
//	For a simple implementation I have written all the code in a single class (including the callbacks).
//		You are free it break it apart into multiple classes, if that is your programming style or helps
//		you manage the code more modularly.
//	REMEMBER !! ZK client library is single thread - Watches & CallBacks should not be used for time consuming tasks.
//		Ideally, Watches & CallBacks should only be used to assign the "work" to a separate thread inside your program.
public class DistProcess implements Watcher
																		, AsyncCallback.ChildrenCallback
{
	ZooKeeper zk;
	String zkServer, pinfo;
	boolean isMaster=false;
	boolean initalized=false;
	String workerNode, workerID;

	DistProcess(String zkhost)
	{
		zkServer=zkhost;
		pinfo = ManagementFactory.getRuntimeMXBean().getName();
		System.out.println("DISTAPP : ZK Connection information : " + zkServer);
		System.out.println("DISTAPP : Process information : " + pinfo);
	}

	void startProcess() throws IOException, UnknownHostException, KeeperException, InterruptedException
	{
		zk = new ZooKeeper(zkServer, 10000, this); //connect to ZK.
	}

	void initalize()
	{
		try
		{
			runForMaster();	// See if you can become the master (i.e, no other master exists)
			isMaster=true;
			getTasks(); // Install monitoring on any new tasks that will be created.
			// TODO monitor for worker tasks?[DONE]
			getFinishedTasks(); // Install monitoring on any new finished tasks that will be created.
			getWorkers(); // Install monitoring on any new workers that will be created.
		}catch(NodeExistsException nee) // TODO: What else will you need if this was a worker process? [DONE]
		{
			try
			{
			//The node is not a could not become the master so create a sequential ephemeral worker znode for it.
			createWorker();
			isMaster=false;
			}
			catch(UnknownHostException uhe)
			{ System.out.println(uhe); }
			catch(KeeperException ke)
			{ System.out.println(ke); }
			catch(InterruptedException ie)
			{ System.out.println(ie); }
		} 
		catch(UnknownHostException uhe)
		{ System.out.println(uhe); }
		catch(KeeperException ke)
		{ System.out.println(ke); }
		catch(InterruptedException ie)
		{ System.out.println(ie); }

		System.out.println("DISTAPP : Role : " + " I will be functioning as " +(isMaster?"master":"worker with worker ID: "+ workerID));

	}

	// Master fetching task znodes...
	void getTasks()
	{
		zk.getChildren("/dist07/tasks", this, this, null);  
	}

	// Try to become the master.
	void runForMaster() throws UnknownHostException, KeeperException, InterruptedException
	{
		//Try to create an ephemeral node to be the master, put the hostname and pid of this process as the data.
		// This is an example of Synchronous API invocation as the function waits for the execution and no callback is involved..
		zk.create("/dist07/master", pinfo.getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
	}

	//Fetching the current workers and set watcher for new workers
	void getWorkers()
	{
		zk.getChildren("/dist07/workers", this, this, null);
	}

	//Fetching the finished tasks and set watcher for new finished tasks
	void getFinishedTasks()
	{
		zk.getChildren("/dist07/finishedTasks", this, this, null);
	}

	//Creating an ephemeral sequential znode for workers
	void createWorker() throws UnknownHostException, KeeperException, InterruptedException
	{
		//Try to create an ephemeral node to be the worker, put idle as the data when created.
		//Set worker node and parse the worker node to get the worker id.
		workerNode = zk.create("/dist07/workers/worker-", "idle".getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
		workerID = workerNode.replace("/dist07/workers/worker-", "");
	}

	//Fetching the data(idle/busy) of a worker with a callback and set watcher for data update
	void getData()
	{
		zk.getData("/dist07/workers/worker-" + workerID, this, workerDataCallback, null);
	}

	public void process(WatchedEvent e)
	{
		//Get watcher notifications.

		//!! IMPORTANT !!
		// Do not perform any time consuming/waiting steps here
		//	including in other functions called from here.
		// 	Your will be essentially holding up ZK client library 
		//	thread and you will not get other notifications.
		//	Instead include another thread in your program logic that
		//   does the time consuming "work" and notify that thread from here.

		System.out.println("DISTAPP : Event received : " + e);

		if(e.getType() == Watcher.Event.EventType.None) // This seems to be the event type associated with connections.
		{
			// Once we are connected, do our intialization stuff.
			if(e.getPath() == null && e.getState() ==  Watcher.Event.KeeperState.SyncConnected && initalized == false) 
			{
				initalize();
				initalized = true;
			}
		}

		// Master should be notified if any new znodes are added to tasks.
		if(e.getType() == Watcher.Event.EventType.NodeChildrenChanged && e.getPath().equals("/dist07/tasks"))
		{
			// There has been changes to the children of the node.
			// We are going to re-install the Watch as well as request for the list of the children.
			if(isMaster) //To only notify if it is the master
			{
				getTasks();
			}
			
		}

		// Master should be notified if any new znodes are added to finished tasks.
		if(e.getType() == Watcher.Event.EventType.NodeChildrenChanged && e.getPath().equals("/dist07/finishedTasks"))
		{
			// There has been changes to the children of the node.
			// We are going to re-install the Watch as well as request for the list of the children.
			if(isMaster) //To only notify if it is the master
			{
				getFinishedTasks();
			}
		}

		// Master should be notified if any new znodes are added to workers.
		if(e.getType() == Watcher.Event.EventType.NodeChildrenChanged && e.getPath().equals("/dist07/workers"))
		{
			// There has been changes to the children of the node.
			// We are going to re-install the Watch as well as request for the list of the children.
			if(isMaster) //To only notify if it is the master
			{
				getWorkers();
			}
		}

		// Workers should be notified if the data(idle/busy) of a worker changed.
		if(e.getType() == Watcher.Event.EventType.NodeDataChanged && e.getPath().equals("/dist07/workers/worker-" + workerID))
		{
			// There has been changes to the data(idle/busy) of the node.
			// We are going to re-install the Watch as well as request for the list of the children.
			if(!isMaster) //To notify if it is the worker
			{
				getData();
			}
		}
	}

	//Asynchronous callback that is invoked by the zk.getChildren request.
	public void processResult(int rc, String path, Object ctx, List<String> children)
	{

		//!! IMPORTANT !!
		// Do not perform any time consuming/waiting steps here
		//	including in other functions called from here.
		// 	Your will be essentially holding up ZK client library 
		//	thread and you will not get other notifications.
		//	Instead include another thread in your program logic that
		//   does the time consuming "work" and notify that thread from here.

		// This logic is for master !!
		//Every time a new task znode is created by the client, this will be invoked.

		// TODO: Filter out and go over only the newly created task znodes.
		//		Also have a mechanism to assign these tasks to a "Worker" process.
		//		The worker must invoke the "compute" function of the Task send by the client.
		//What to do if you do not have a free worker process?
		System.out.println("DISTAPP : processResult : " + rc + ":" + path + ":" + ctx);
		for(String c: children)
		{
			System.out.println(c);
			try
			{
				//TODO There is quite a bit of worker specific activities here,
				// that should be moved done by a process function as the worker.

				//TODO!! This is not a good approach, you should get the data using an async version of the API.
				byte[] taskSerial = zk.getData("/dist07/tasks/"+c, false, null);

				// Re-construct our task object.
				ByteArrayInputStream bis = new ByteArrayInputStream(taskSerial);
				ObjectInput in = new ObjectInputStream(bis);
				DistTask dt = (DistTask) in.readObject();

				//Execute the task.
				//TODO: Again, time consuming stuff. Should be done by some other thread and not inside a callback!
				dt.compute();
				
				// Serialize our Task object back to a byte array!
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(bos);
				oos.writeObject(dt); oos.flush();
				taskSerial = bos.toByteArray();

				// Store it inside the result node.
				zk.create("/dist07/tasks/"+c+"/result", taskSerial, Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
				//zk.create("/dist07/tasks/"+c+"/result", ("Hello from "+pinfo).getBytes(), Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			}
			catch(NodeExistsException nee){System.out.println(nee);}
			catch(KeeperException ke){System.out.println(ke);}
			catch(InterruptedException ie){System.out.println(ie);}
			catch(IOException io){System.out.println(io);}
			catch(ClassNotFoundException cne){System.out.println(cne);}
		}
	}

	DataCallback workerDataCallback = new DataCallback()
	{
		@Override
		public void processResult(int rc, String path, Object ctx, byte[] bytes, Stat stat)
		{
			switch(Code.get(rc))
			{
				case OK:
					String taskNodeName = new String(bytes, StandardCharsets.UTF_8);
					if(!"idle".equals(taskNodeName))
					{
						System.out.println("WORKER: Query " +taskNodeName);
						zk.getData("/dist07/tasks/" + taskNodeName, null, taskDataCallback, this);
					}
					break;
				case CONNECTIONLOSS:
					System.out.println("WORKER: Connection lost!");
					break;
			}
		}
	};

	DataCallback taskDataCallback = new DataCallback()
	{
		@Override
		public void processResult(int rc, String path, Object ctx, byte[] bytes, Stat stat)
		{
			switch(Code.get(rc))
			{
				case OK:
					//TODO
					break;
				case NONODE:
					System.out.println("WORKER: The task is already finished.");
					break;
				case CONNECTIONLOSS:
					System.out.println("WORKER: Connection lost!");
					break;
			}
		}
	};


	public static void main(String args[]) throws Exception
	{
		//Create a new process
		//Read the ZooKeeper ensemble information from the environment variable.
		DistProcess dt = new DistProcess(System.getenv("ZKSERVER"));
		dt.startProcess();

		//Replace this with an approach that will make sure that the process is up and running forever.[DONE]
		//Thread.sleep(20000);
		while(true){
			Thread.sleep(500);
		} 
	}
}
