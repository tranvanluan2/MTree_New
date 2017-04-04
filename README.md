This project demonstrates experiments in the paper http://www.vldb.org/pvldb/vol9/p1089-tran.pdf

Sample parameters to run the built jar file:  

--algorithm microCluster_new --R 0.28 --W 100000 --k 50 --slide 5000 --datafile C:\Users\Luan\MTree\gaussian.txt 
     --numberWindow 180 --samplingTime 100 --resultFile result.txt


--algorithm: algorithm to run: microCluster_new, mesi, abstractC, lue, due, exactStorm
--R: radius threshold
--k: neighbor count threshold 
--datafile: data file with structure as follows: each line contains values of attributes of one data point,  separated by ","
0.04,84.87,23.436
0.09,85.12,23.438
0.14,84.92,23.441
0.07,84.74,23.443
0,84.57,23.441
0.02,84.41,23.433
-0.06,83.93,23.428
-0.01,82.94,23.426
-0.07,83.12,23.417
-0.19,82.2,23.415
-0.11,81.44,23.414


--numberWindow: Number of windows to run
--samplingTime: the sampling time to sample the memory, cpu usage
--resultFile: output file contains the id of outliers.

