![ZK Model](https://github.com/Narimanzk/ZooKeeper/blob/main/updated_zk_model.png)
The above illustrates the high-level model designed for this project. 

Blue cells represent persistent znodes while orange cells are ephemeral. Detailed explanations about each category of znodes are provided in the sections below.

## Description
The model above describes our Zookeeper design. The top node is the __dist07__ znode. This is a persistent node under which all znodes for the system are stored. Under __dist07__ there are 5 znodes.

First is the __master__ node, it is ephemeral and has no children, as its only job is to assign workers to tasks. Second, there is the __tasks__ znode which is a permanent node that stores sequential task nodes as children. There is also a __workers__ persistent znode, under which a sequential znode is added for each worker (member process) that gets added to the distributed system. The worker znode is assigned a unique sequential ID and stores the worker's status as its data. The status is either set to 'idle' if the worker is unassigned or __taskID__ if it has been assigned a task where __taskID__ is the unique identifier of the task to allow the worker to retrieve the assigned task correctly.

When the client requests a computation, a task node gets created under tasks, with a unique ID and the serialized job (computation) object, and stored as the data of this task. The master picks tasks and assigns them to workers. Then a worker completes the job/computation contained in the task it was assigned, it serializes the job object which is added as the data to a __result__ persistent znode added under the __task__ znode with the matching ID. 

Finally, there is a persistent znode called __finishedTasks__. When a worker finishes a task, the task is removed from the __tasks__ znode, and an ephemeral-sequential child is added under the __finishedTasks__ znode with its data being the workerID of the worker that finished the task. This is to allow the master to keep track of when workers become idle again after their computation terminates. 


## Implementation
Our Zookeeper design is implemented as follows. When a member process is started, Zookeeper starts up. Then the member process starts the initialize phase where it first tries to become the master. If it succeeds in becoming master, it then installs monitoring on all new tasks that will be created, and on all future finished tasks. It also installs monitoring on any future workers. If it cannot become master, it becomes a worker instead, with its status set to "idle".  This logic is implemented in the function __initalize()__.

The master uses two local data structures to track the assignment of the tasks to workers. This is to avoid getting data from znodes every time a change is made to the system. First, there is a hashmap of workers in which each worker is saved with its name (path) as the key and a boolean value as the value. This boolean value is set to true whenever a worker is busy and false when it is available. The second helper data structure is a list of assigned tasks that the master used to track which tasks have been assigned to the workers.

Once the master is running, it watches for events. There are 5 events. For events 2., 3. and 4., if the process is the master, then the event triggers the __zk.getChildren__ callback function which calls __processResult__. Then, depending on the event, specific pieces of the code are executed and the response is sent back to the callback object.

  - A new member process connection is added.
  
      In this case, there is a new member process so we start the initialize phase to make the process either the master or a worker.
  - A task znode is added under __/dist07/tasks__ (new task requested by client).
  
      It iterates through the tasks, and for each unassigned task, we then pick the first worker available from under __/dist07/workers__ and assign it to the task. If there are no workers available it keeps trying until a worker can be assigned. The retry phase is done asynchronously using a thread.
  - A worker znode was added under __/dist07/workers__ (new member process has started).
  
      It adds all new workers to the __workers__ hashmap locally.
  - A znode is added under __/dist07/finishedTasks__.
  
      All children nodes (workers) of __/finished__ are deleted and their corresponding workers are added to the  __workers__ hashmap as available workers to be assigned to future coming tasks.
  - The status of a worker-ID znode is changed (__NodeDataChanged__).
  
  The idea is that if the status of a worker has changed to 'busy' it has been assigned a task by the master, hence we should begin computing it. Thus, on the worker's process, we call WorkerDataCallback, which, if the worker status is 'busy' calls TaskDataCallback. This performs the computation, then serializes the result and adds it to a result node, under __/dist07/tasks/task-ID/result__. Then it sets the worker's status to 'idle' and creates a new child under __/dist07/finishedTasks__ with __workerID__ as the name. 

Note that processes stay up and running forever until they are manually shut down. 

Finally, as mentioned above to avoid holding up the ZK client library thread in callbacks, we used threading. More specifically, all the code in the function __processResult()__ is processed asynchronously within another thread.
