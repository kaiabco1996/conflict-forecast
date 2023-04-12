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

1. A set of predicted trajectories in JSON format. A [`Trajectory`](src/main/kotlin/aero/airlab/challenge/conflictforecast/api/Trajectory.kt)
   is defined by an ID and a list of [`Waypoint`](src/main/kotlin/aero/airlab/challenge/conflictforecast/api/Trajectory.kt).

1. A set of separation requirement regions in JSON format. A [`SeparationRequirement`](src/main/kotlin/aero/airlab/challenge/conflictforecast/api/SeparationRequirement.kt)
   is a circular region defined by a center and a radius.

1. The required REST service request and response body is specified by [`ConflictForecastRequest`](src/main/kotlin/aero/airlab/challenge/conflictforecast/api/ConflictForecastRequest.kt)
   and [`ConflictForecastResponse`](src/main/kotlin/aero/airlab/challenge/conflictforecast/api/ConflictForecastResponse.kt)

1. For the implementation of the conflict forecast algorithm, you can use this simple brute force method:
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

1. *The code in package [`forecast`](src/main/kotlin/aero/airlab/challenge/conflictforecast/forecast)
   are for our reference and is not provided to candidate*

# Required Tasks

1. Implement the algorithm to compute conflicts given a set of trajectories.
   - Identify and build components (if any) that can be re-used for other services in future.
   - Consider how to include logs of different detail levels to the implementation to help analyze how forecasted results were derived.
1. Implement the REST service that encapsulate this algorithm.
1. Write the necessary unit tests to verify that the service works according to specification.

# Good to Have Tasks

1. Create another service that will return the conflicts as a [GeoJSON Feature Collection](https://geojson.org/)
   which can be visualized in an online viewer like [geojson.io](https://geojson.io)

# Stretch Tasks

1. Create a web frontend for invoking the service and visualizing the trajectories and conflicts. 
   A simple form can be used to define the trajectories or simply accept them as JSON in a text box.

# Possible Live Tasks (for our reference)

1. Add support for polygonal separation regions.
2. Add support for vertical separation

# Possible Questions (for our reference)

1. How can the algorithm be made faster?
   - Use of spatial partitioning when computing distance between aircraft
   - Coarse grain filtering of trajectories

1. How can the algorithm be scaled horizontally for a single request?
   - Possible solutions:
     - Compute each time step in parallel
     - Spatially partition the trajectories
   - What are the considerations in terms of thread safety?
