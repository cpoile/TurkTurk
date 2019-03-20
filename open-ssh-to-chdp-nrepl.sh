#!/bin/bash

ssh chp3 -N -L 60606:localhost:60606 &

echo "Opened up port on 60606."
