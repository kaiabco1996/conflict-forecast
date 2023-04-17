# Objective

Create a REST service to forecast potential air traffic conflicts 
given predicted trajectories.

# Background

In the context of Air Traffic Management, a conflict occurs when there is a
breakdown in either lateral or vertical separation.

Lateral separation is the spacing between two aircraft ignoring their altitude.
Vertical separation is the spacing between two aircraft based on their altitude and
clearances only.

To simplify this challenge, vertical separation shall not be considered.

In general, aircrafts travel on pre-defined routes like cars on a highway.
The trajectory of an aircraft can be predicted based on its assigned route, speed,
weather condition, route constraints and various other factors.

In this challenge, the predicted trajectories of aircrafts are given to you as
a list of waypoints and their estimated flyover time (ETO).
The ETO specifies the predicted time at which the aircraft will fly over the 
associated waypoint. Waypoints are specified as a pair of longitude and latitude.

The required lateral separation between aircraft is different depending on which
spatial region the aircraft is currently in.

# Provided Resources

1. A simple library for Geospatial computations is provided
   - Use function [`GeodeticCalc.geodeticCalcWSSS()`](src/main/kotlin/aero/airlab/challenge/conflictforecast/geospatial/GeodeticCalc.kt)
     to create an instance of GeodeticCalc.
   - GeodeticCalc should have all the methods you require to perform geospatial 
     computations required for this challenge

1. The required REST service request and response body is specified by [`ConflictForecastRequest`](src/main/kotlin/aero/airlab/challenge/conflictforecast/api/ConflictForecastRequest.kt)
   and [`ConflictForecastResponse`](src/main/kotlin/aero/airlab/challenge/conflictforecast/api/ConflictForecastResponse.kt)

1. Two files consisting of two `ConflictForecastRequest` in JSON format are provided in
   [src/test/resources](src/test/resources). 

   Each request consists of a set of predicted trajectories and separation requirements.
   
   A [`Trajectory`](src/main/kotlin/aero/airlab/challenge/conflictforecast/api/Trajectory.kt)
   is defined by an ID and a list of [`Waypoint`](src/main/kotlin/aero/airlab/challenge/conflictforecast/api/Trajectory.kt).
   
   A [`SeparationRequirement`](src/main/kotlin/aero/airlab/challenge/conflictforecast/api/SeparationRequirement.kt)
   is a circular region defined by a center and a radius.

1. For the implementation of the conflict forecast algorithm, you shall use this simple brute force method:
   1. Let current_time = earliest timestamp of the first Waypoint of all trajectories
   2. Repeat until current_time > last Waypoint of all trajectories
      1. Predict the position of each aircraft at current_time
         - Linearly interpolate the position using distance and time difference between 
           the previous and next Waypoint
         - Compute the expected speed of the aircraft between the previous and next Waypoint and 
           make use of functions `GeodeticCalc.headingAndDistanceTo` and `GeodeticCalc.nextPointFrom`
           to compute the predicted position at current_time
      1. Compute the lateral distance between each aircraft at current_time
      1. Determine the lateral separation region each aircraft is currently in
      1. Check whether there is separation breakdown between any of the aircraft.
         For two aircraft of different separation regions, use the larger separation requirement.
      1. Create `Conflict` objects when conflicts are detected.
      1. Increment current_time by 5 seconds

# Required Tasks

1. Implement the algorithm to compute conflicts given a set of trajectories.
   - You must implement the simple brute force method described above.
   - You can implement a better algorithm as a "good to have" task (see below).
1. If you wish to, you can code in Java instead of Kotlin. Just write your source code under `src/main/java`.
   Java code should have no problem using the provided Kotlin classes, if not, you are free to modify or translate the
   provided code to Java.
1. Identify and build components (if any) that can be re-used for implementing other services related to trajectory analysis in future.
1. Consider how to include logs of different detail levels to the implementation to help analyze how forecasted results were derived.
1. Implement the REST service that encapsulate this algorithm.
1. Write the necessary unit tests to verify that the service works according to specification.

# Good to Have Tasks

1. Create another service that will return the conflicts as a [GeoJSON Feature Collection](https://geojson.org/)
   which can be visualized in an online viewer like [geojson.io](https://geojson.io)
2. Use a more performant (but no less accurate) algorithm to compute conflicts and compare its performance
   with the given brute force method.

# Stretch Tasks

1. Create a web frontend for invoking the service and visualizing the trajectories and conflicts. 
   A simple form can be used to define the trajectories or simply accept them as JSON in a text box.
