#!/bin/bash

for i in $(seq 10) 
  do
    echo "Evaluating part $i..."
    java build_tagger "sents.train"$i"_trn" "sents.train"$i"_tst" model_file
    java run_tagger "sents.train"$i"_tst" model_file sents.out
  done
