# TurkTurk 

Use Amazon Mechanical Turks in social psychology experiments

[Why](#why) | [Technical Specs](#tech_specs) | [Features](#features) | [Usage](#usage) | [Published In](#published_in)

## Overview 

This is a web server written in Clojure that creates RESTful JSON endpoints that you can use to run experiments with Mechanical Turks. It lets you use Mechanical Turks as if they were regular "in-person" participants in a lab experiment.

## Why?

The current literature uses Turks for experiments in a pretty narrow way. Turks are easy to use for data collection that can be done alone, such as surveys. The most interactive experiments using Turks are the behavioral economics style decision-making tasks. But decision-making tasks done alone are not truly interactive in the sense of a social psychology experiment. 

To the best of my knowledge, few researchers have used Turks as participants in social psychological experiments that involve multiple participants completing a collaborative task, and interacting and communicating during an experimental session.

Why is that? Probably the main issue is: there's no automated way for a researcher to assign the Turk a condition and track that Turk across multiple stages and different services.
 
This system allows you to do that. You can take a Mechanical Turk worker and treat them as if they were a student participant. You can:
- give them a pretest
- randomly assign them to a condition
- send them to internal or external systems where they can participate in that interactive social psych experiment
- then return them to the Amazon Mechanical Turk system with a code to prove they participated
- contact the Amazon system to collect the codes (showing who participated)
- disburse the payments for participation
- and award winnings (if it was a lottery based experiment)

## Tech Specs

- Clojure and Compojure to build the web server endpoints
- MongoDB for the database
- uses clojure's excellent concurrency support (eg., ids are never duplicated, etc.)

## Features

- unique ids are tracked
  - all links to and from this server include the participants unique id
- include multiple steps, surveys, etc. 
  - Since the id is included in all links, it's easy to track participants across services and join all data together during data analysis. Eg:
    - first send them to a survey tool (like Survey Monkey or Qualtrics)
    - then to the experiment's task
    - then halfway through send them to another survey
    - then continue the experiment task
    - then return to this server to get their completion code and send them back to Amazon's system to get paid.  
- participants can be given a time-limit
   - eg., if they don't finish the experiment in 2 hours, they won't be paid, and their participant id and other tokens expire
- guarantee participation: 
  - when participants are sent to the internal or external experiment system, you can pass a unique completion token. When they finish the experiment, the link they follow back to _this_ system should include that token. This way, you know that the participant actually completed the task (i.e., they aren't just returning right away to get credit for a task they didn't complete)
- prevent double-dipping:
  - since Amazon sends the Turk's id, this system records that and will prevent the same Turk from doing the task multiple times (unless you want that...)
- know when you have enough data: 
  - there is code here to query the database for a list of current participants and how many have completed, per condition (i.e., you can decide when you have enough data points and turn off the Turk task)
- 

## Usage

TODO: I need to create a how-to here. 

If there is anyone who wishes to use this software, please open an issue above and I'll fill out the instructions with a walkthrough of how to change the settings.

For now, if you want to explore, the entry point is the `-main` function on [here in the exp_condition_web.clj](https://github.com/cpoile/TurkTurk/blob/515b6bfb7f4bba1a1bfded09dff69a948ecdd659/src/exp_condition/exp_condition_web.clj#L543) file.

## Published in

This system was used to gather data for:

Poile, C. (2017). Why would I help my coworker? Exploring asymmetric task dependence and the Self-determination Theory internalization process. Journal of Experimental Psychology: Applied, 23(3), 354-368. http://doi.org/10.1037/xap0000128


Poile, C. (2018). When Power Hurts: Task-dependent Resource Control Creates Temporary Discomfort that Motivates Helping Behaviour. Canadian Journal of Administrative Sciences / Revue Canadienne Des Sciences de l'Administration. http://doi.org/10.1002/cjas.1499

