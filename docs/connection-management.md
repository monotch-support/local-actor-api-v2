# Connection Management

Even after successful connection of the initial AMQP connection(s), the delivery/subscription needs to be monitored by the client by polling the related endpoint.

!!! warning
    **If a created delivery or subscription is not polled for it’s status, it will be deprovioned (deleted) after a certain time.**
    

The following changes need to be processed when polling deliveries/subscriptions:

- Status changes
- Added AMQP endpoints
- Removed AMQP endpoints

In order to illustrate this concept a simplified sequence diagram is shown below:

```plantuml
@startuml
skinparam sequence {
    ParticipantPadding 30
    BoxPadding 20
    ArrowColor black
    LifeLineBorderColor black
    LifeLineBackgroundColor white
    BoxBorderColor black
    BoxBackgroundColor #EEEEEE
}

' Participants
box "Local Actor" #cfffff
participant "Client" as Local
end box

box "Interchange" #cfffff
participant "Local Actor API" as API
participant "AMQP endpoint" as AMQP
end box



== Data Producer ==

box "Data Producer" #white
Local -> API : 1 Create delivery
API --> Local : 2 Delivery handle

loop As long delivery exists
    Local -> API : 3 Get delivery
    API --> Local : 4 Delivery details\n(contains AMQP endpoints for delivery)

    loop For each new AMQP endpoint
        Local -> AMQP : 5 Connect
    end
end
end box

== Data Consumer ==

box "Data Consumer" #white
Local -> API : 1 Create subscription
API --> Local : 2 Subscription handle

loop As long subscription exists
    Local -> API : 3 Get subscription
    API --> Local : 4 Subscription details\n(contains AMQP endpoints for subscription)

    loop For each new AMQP endpoint
        Local -> AMQP : 5 Connect
    end
end
end box
@enduml
```