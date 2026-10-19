#!/bin/bash
awslocal sqs create-queue --queue-name signal-raw-messages
awslocal sqs create-queue --queue-name signal-scored-items
awslocal sqs create-queue --queue-name signal-interruption-signals
echo "SQS Queues created successfully."
