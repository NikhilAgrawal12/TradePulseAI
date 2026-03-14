import { useState } from 'react'
import { ChatInput } from './components/ChatInput'
import ChatMessages from './components/ChatMessages'
import './App.css'


function App() {

    const [chatMessages,setChatMessages] = useState([{
        message: "hello chatbot",
        sender: "user",
        id: "id1"
    }, {
        message: "Hello! How can I help you?",
        sender: "robot",
        id: "id2"
    }, {
        message: "Can you get me today's date?",
        sender: "user",
        id: "id3"
    },{
        message: "Today is February 28",
        sender: "robot",
        id: "id4"
    }]);

    // whenever there is any change in chatMessages, React re-renders the component that owns the state.

    return (
        <div className="app-container">

            <ChatMessages
                chatMessages={chatMessages}
            />

            <ChatInput
                chatMessages={chatMessages}
                setChatMessages={setChatMessages}
            />
        </div>

    );
}

export default App
