const toggleButton = document.getElementById('cambiar-usuario');
const studentFields = document.querySelector('.student-fields');
const teacherFields = document.querySelector('.teacher-fields');
const formTitle = document.getElementById('form-title');
const modeHelper = document.getElementById('mode-helper');
const addClassButton = document.getElementById('agregar-clase');
const savedClassesContainer = document.getElementById('clases-guardadas');
const classesInput = document.getElementById('clases-json');
const materiaSelect = document.getElementById('materia');
const startSelect = document.getElementById('hora-inicio');
const endSelect = document.getElementById('hora-fin');
const groupSelect = document.getElementById('grupo-profesor');
const daySelect = document.getElementById('dia');

let isTeacherMode = false;
let savedClasses = [];

function renderMode() {
    if (!studentFields || !teacherFields || !toggleButton) return;

    studentFields.hidden = isTeacherMode;
    teacherFields.hidden = !isTeacherMode;
    toggleButton.textContent = isTeacherMode ? 'Añadir alumno' : 'Añadir profesor';
    formTitle.textContent = isTeacherMode ? 'Agregar profesor' : 'Agregar alumno';
    modeHelper.textContent = isTeacherMode
        ? 'Completa los datos para registrar a un profesor.'
        : 'Completa los datos para registrar a un alumno.';
}

function renderSavedClasses() {
    if (!savedClassesContainer) return;

    if (savedClasses.length === 0) {
        savedClassesContainer.innerHTML = '<p class="empty-state">Aún no se han agregado clases.</p>';
    } else {
        savedClassesContainer.innerHTML = savedClasses
            .map((clase, index) => `
                <article class="clase-card">
                    <p class="materia">${clase.materia}</p>
                    <p class="meta">${clase.dia} · ${clase.horaInicio} - ${clase.horaFin}</p>
                    <p class="meta">Grupo: ${clase.grupo}</p>
                </article>
            `)
            .join('');
    }

    if (classesInput) {
        classesInput.value = JSON.stringify(savedClasses);
    }
}

function addClass() {
    if (!materiaSelect || !startSelect || !endSelect || !groupSelect || !daySelect) return;

    const nuevaClase = {
        materia: materiaSelect.value,
        horaInicio: startSelect.value,
        horaFin: endSelect.value,
        grupo: groupSelect.value,
        dia: daySelect.value
    };

    savedClasses.push(nuevaClase);
    renderSavedClasses();

    materiaSelect.selectedIndex = 0;
    startSelect.selectedIndex = 0;
    endSelect.selectedIndex = 0;
    groupSelect.selectedIndex = 0;
    daySelect.selectedIndex = 0;
}

if (toggleButton) {
    toggleButton.addEventListener('click', () => {
        isTeacherMode = !isTeacherMode;
        renderMode();
    });
}

if (addClassButton) {
    addClassButton.addEventListener('click', addClass);
}

renderMode();
renderSavedClasses();