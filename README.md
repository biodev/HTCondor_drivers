#### HTCondor_drivers

**Installation**
```
git clone https://github.com/broadgsa/gatk-protected.git
git clone https://github.com/biodev/HTCondor_drivers.git
cd gatk-protected
git checkout tags/3.3

mkdir public/gatk-queue/src/main/scala/org/broadinstitute/gatk/queue/engine/condor
cp ../HTCondor_drivers/Queue/CondorJob* public/gatk-queue/src/main/scala/org/broadinstitute/gatk/queue/engine/condor/
mvn verify
cp target/*.jar /desired/location/
```

**Usage**

* Specify ***Condor*** as the job runner e.g.:
```
java -Djava.io.tmpdir=tmp -jar /path/to/Queue.jar -S my_script.scala -jobRunner Condor
```
* It's a good idea to specify both the jobOutputFile and jobErrorFile when runnings jobs. e.g.:
```
val my_job = new UnifiedGenotyper
my_job.jobOutputFile="my_job.out"
my_job.jobErrorFile="my_job.err"
...
add(my_job)
```
