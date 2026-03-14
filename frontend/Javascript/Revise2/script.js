let fullname= "Nikhil Agrawal";
console.log(fullname);

const age=25;    //value cant be changed
console.log(age);

let x=null;
console.log(x);

let y;
console.log(y);

let isFollow=false;
console.log(isFollow);

{
    let fullname= "Nikhil";
    console.log(fullname);
}

console.log(typeof isFollow);

    const student = {
        name: "Nikhil",
        marks: 50,
        isPass: true
};

console.log(student.name);
console.log(student["marks"]);

student.name = "Nikhil Agrawal";
console.log(student.name);

let a=2;
let b=3;
console.log("a+b=",a+b);

console.log("a**b=",a**b);

let mode="dark";
let color;

if(mode == "dark"){
    color="black";
}
else{
    color = "white";
}

console.log(color);
console.log("Sum of first 5 natural numbers:");
let sum =0;
for(let i=1;i<=5;i++){
    sum+=i;
}
console.log("Sum: ",sum);

let len=0;

for(let i of fullname){
    console.log(i);
    len++;
}

console.log("Length: ",len);

for(let i in student){
    console.log(student[i]);
}

console.log("All even numbers: ")

for(let i=0;i<=100;i++){
    if(i%2===0){
        console.log(i);
    }
}

// let gameno = 8;
// let num = prompt("Guess the number: ");
//
// while(num!=gameno) {
//     num = prompt("Your guess was wrong, enter again: ");
// }
//
// console.log("You guessed the right number i.e. ",num);

console.log("String length: ",fullname.length);

console.log(fullname[0]);

console.log(`My full name is ${fullname}`);

let cname = fullname.toUpperCase();
console.log(`My name is ${cname}`);

console.log(fullname.slice(0,4));

let marks = [34,45,49,50];

console.log(marks);

for(let i of marks){
    console.log(i);
}


let marksa = [30,35,40,45,50];
let suma=0;
let cnt=0;

for(let i of marksa){
    suma+=i;
    cnt++;
}

console.log("Average: ",suma/cnt);

marksa.push(55);
console.log(marksa);

console.log("Deleted Item: ",marksa.pop());

console.log(marksa);

console.log(marksa.toString());

// Function

function myFunction(msg){
    console.log(msg);
}

myFunction("HI!");

function add(a,b) {
    return a + b;
}

console.log(add(2,3));


const arrowSum = (a,b) => {
    return a + b;
}

console.log(arrowSum(2,3));


const numVowels = (str) => {
    let count=0;
    for(let i of str){
        if(i==='a' || i==='e' || i==='i' || i==='o' || i==='u'){
            count++;
        }
    }
    return count;
}

console.log(numVowels("aeioudbcf"));

let cities = ["boston", "newyork","california","atlanta"];


cities.forEach((city) => {console.log(city.toUpperCase())});


let arr = [1,2,3,4,5,6];

// arr.forEach((i) => {
//     console.log(i*i);
// });

arr = arr.map((i) => {
    return i*i;
})

console.log(arr);

let newArr = arr.filter((i) => {
    return i%2===0;
})
console.log(newArr);

// Revise functions
// const mult = (a,b) => {
//     return a + b;
// }
//
// console.log(mult(2,3));
//
// let arra = [2,3,4,5,6];
//
// arra = arra.map((i) => {
//     return i*i;
// })
//
// console.log(arra);

//Class and Object

const student3 = {
    name: "Nik",
    marks: 50,
    showName() {
        console.log(this.name)
    }
};

student3.showName();

const employee = {
    calcTax() {
        console.log("tax rate is 10%");
    }
};

const nikhilAgrawal = {
    salary: 50000
};

nikhilAgrawal.__proto__ = employee;
nikhilAgrawal.calcTax();


const rohan = {
    salary: 60000,
    calcTax() {
        console.log("tax rate is 20%");
    }
};

rohan.__proto__ = employee;
rohan.calcTax();

//class

class ToyotaCar {
    constructor(brand){
        console.log("creating new object");
        this.brand = brand;
    }

    start() {
        console.log("start");
    }

    stop(){
        console.log("stop");
    }

    setType(type) {
        this.type = type;
    }
}

let fortuner = new ToyotaCar("Fortuner");
fortuner.setType("SUV");
fortuner.start();
console.log(fortuner.brand);
console.log(fortuner.type);


//Inheritance

class Parent {
    hello() {
        console.log("hello");
    }
}

class Child extends Parent {

}

let c1 = new Child();
c1.hello();

// super();

class Person {
    constructor(name) {
        console.log("parent constructor")
        this.specises = "Homosepiens";
        this.name=name;
    }

    eat() {
        console.log("eat");
    }

}

class Engineer extends Person {
    constructor(name,branch) {
        console.log("child constructor");
        super(name);  // without calling parent constructor we cannot use child constructor
        this.branch = branch;
    }

    work(){
        super.eat();
        console.log("work");
    }
}

let engObj = new Engineer("Nikhil","CS");
console.log(engObj);
engObj.work();


class User {
    constructor(name, email) {
        this.name = name;
        this.email = email;
    }

    viewData() {
        console.log("Name: ",this.name);
        console.log("Email: ",this.email);
    }

}

let student1 = new User("Nik","nik@gmail.com");
student1.viewData();

//Error Handling

let z=3;
let w=5;

console.log(z+w);

try {
    console.log(z-v);
} catch(err) {
    console.log(err);
}

console.log(z*w);


// Callbacks, Promises and Async Await

console.log("one");
console.log("two");

setTimeout(() => {console.log("hello")}, 1000);  // Execute after 1 seconds

console.log("three");
console.log("four");

//Callback

function sum2(a,b) {
    console.log(a+b);
}

function calculator(a,b,sumCallback) {
    sumCallback(a,b);
}

calculator(2,3,sum2);



// function getData(data,getNextData){
//
//     setTimeout(() => {
//         console.log("Data: ",data)
//         if(getNextData){
//             getNextData();
//         }
//     },2000);
//
// }
//
// // callback hell
//
// getData(1,
//     () => {getData(2,
//         () => {getData(3)})});







// Promises
// Promise is an object in javascript with three states : pending, resolved (fulfilled) and rejected
let promise = new Promise((resolve,reject) => {
    console.log("I am a promise")
    resolve("success");
})

promise.then((res) => {console.log("Promise fulfilled",res)});


function getData(data){

    return new Promise((resolve,reject) => {
        setTimeout(() => {
            console.log("Data:",data)
            resolve(`${data} successfully fetched`);
        },3000);
    });
}

// // Promise chain
//
// getData("one")
//     .then((res) => {
//         console.log(res);
//         return getData("two"); // return the next Promise
//     })
//     .then((res2) => {
//         console.log(res2);
//         return getData("three"); // return the next Promise
//     })
//     .then((res3) => {
//         console.log(res3);
//     })
//     .catch(err => console.error(err)); // handle errors in one place




// Async Await

(async function (){
    let y = await getData("one");
    console.log(y);   // will get the resolve value
    await getData("two");
    await getData("three");
})();


//Async-Await > Promise Chain > callback hell