import RobotProfileImage from "../assets/robot.png";
import UserProfileImage from "../assets/user.png";
import'./ChatMessage.css'

export function ChatMessage({ message, sender }) {
    // const message = prop.message;
    // const {message} = prop;
    // We will get prop objects with message and sender properties

    return (
        <div className={
            sender === "user"
                ? "chat-message-user"
                : "chat-message-robot"
        }>
            {sender === "robot" && (
                <img src={RobotProfileImage} alt="robot" className="chat-message-profile"/>
            )}
            <div className="chat-message-text">
                {message}
            </div>

            {sender === "user" && (
                <img src={UserProfileImage} alt="user" className="chat-message-profile"/>
            )}
        </div>
    );
}
