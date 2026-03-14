let x =0;

const winningCombos = [
    ["B1","B2","B3"], // row 1
    ["B4","B5","B6"], // row 2
    ["B7","B8","B9"], // row 3
    ["B1","B4","B7"], // col 1
    ["B2","B5","B8"], // col 2
    ["B3","B6","B9"], // col 3
    ["B1","B5","B9"], // diagonal 1
    ["B3","B5","B7"]  // diagonal 2
];

let xMoves = [];
let oMoves = [];
let xTurn = true;

let boxes = document.querySelectorAll(".gameBox");

const checkWinner = () => {
    let currentMoves = xTurn ? xMoves : oMoves;

    for(let combo of winningCombos){
        // check if all elements of combo are in currentMoves
        if(combo.every(id => currentMoves.includes(id))){
            alert(`${xTurn ? "X" : "O"} wins!`);
            window.location.reload();
            return true;
        }
    }
    return false;
};


boxes.forEach(box => {
    box.addEventListener("click", () => {
        // Prevent clicking on an already filled box
        if(box.classList.contains("backgroundX") || box.classList.contains("backgroundO")) return;

        if(xTurn){
            box.classList.add("backgroundX");
            xMoves.push(box.id);
        } else {
            box.classList.add("backgroundO");
            oMoves.push(box.id);
        }

        if(checkWinner()) return; // stop after win

        xTurn = !xTurn; // switch turn
    });
});





