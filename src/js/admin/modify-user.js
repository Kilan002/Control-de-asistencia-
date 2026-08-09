document.addEventListener('DOMContentLoaded', () => {
    const editButtons = document.querySelectorAll('[data-action="edit"]');
    const deleteButtons = document.querySelectorAll('[data-action="delete"]');
    const deleteModal = document.getElementById('delete-modal');
    const editModal = document.getElementById('edit-modal');
    const editHelper = document.getElementById('edit-helper');
    const editTitle = document.getElementById('edit-title');
    const studentFields = document.getElementById('student-fields');
    const teacherFields = document.getElementById('teacher-fields');
    const photoPreview = document.getElementById('photo-preview');
    const editForm = document.getElementById('edit-form');
    const classesContainer = document.getElementById('profesor-clases-guardadas');
    const saveClassButton = document.getElementById('guardar-clase');
    const clearClassButton = document.getElementById('limpiar-clase');

    let professorClasses = [];
    let editingClassIndex = -1;

    function openModal(modal) {
        modal.classList.add('active');
        modal.setAttribute('aria-hidden', 'false');
    }

    function closeModal(modal) {
        modal.classList.remove('active');
        modal.setAttribute('aria-hidden', 'true');
    }

    function renderProfessorClasses() {
        if (!classesContainer) return;

        if (professorClasses.length === 0) {
            classesContainer.innerHTML = '<p class="empty-state">Aún no se han registrado clases.</p>';
            return;
        }

        classesContainer.innerHTML = professorClasses.map((clase, index) => `
            <article class="clase-card" data-index="${index}">
                <p class="materia">${clase.materia}</p>
                <p class="meta">${clase.dia} · ${clase.horaInicio} - ${clase.horaFin}</p>
                <p class="meta">Grupo: ${clase.grupo}</p>
                <div class="modal-actions" style="gap:0.5rem; margin-top:0.75rem;">
                    <button type="button" class="secondary-button" data-action="edit-class" data-index="${index}">Editar</button>
                    <button type="button" class="secondary-button danger" data-action="delete-class" data-index="${index}">Eliminar</button>
                </div>
            </article>
        `).join('');
    }

    function resetClassEditor() {
        editingClassIndex = -1;
        document.getElementById('edit-materia').selectedIndex = 0;
        document.getElementById('edit-hora-inicio').selectedIndex = 0;
        document.getElementById('edit-hora-fin').selectedIndex = 0;
        document.getElementById('edit-grupo-profesor').selectedIndex = 0;
        document.getElementById('edit-dia').selectedIndex = 0;
        if (saveClassButton) {
            saveClassButton.textContent = 'Guardar clase';
        }
    }

    function fillClassEditor(clase, index) {
        document.getElementById('edit-materia').value = clase.materia;
        document.getElementById('edit-hora-inicio').value = clase.horaInicio;
        document.getElementById('edit-hora-fin').value = clase.horaFin;
        document.getElementById('edit-grupo-profesor').value = clase.grupo;
        document.getElementById('edit-dia').value = clase.dia;
        editingClassIndex = index;
        if (saveClassButton) {
            saveClassButton.textContent = 'Actualizar clase';
        }
    }

    window.openEditModal = function (button) {
        const resultCard = button.closest('.result-card');
        const isTeacher = resultCard?.dataset.role === 'profesor';

        editTitle.textContent = isTeacher ? 'Editar profesor' : 'Editar alumno';
        editHelper.textContent = isTeacher
            ? 'Modifica los datos del profesor y sus clases.'
            : 'Modifica los datos del alumno, incluyendo grupo y foto.';

        studentFields.hidden = isTeacher;
        teacherFields.hidden = !isTeacher;

        document.getElementById('edit-nombre').value = 'María López';
        document.getElementById('edit-matricula').value = '12345';
        document.getElementById('edit-password').value = '';
        document.getElementById('edit-email').value = 'maria@ejemplo.com';
        document.getElementById('edit-grupo').value = 'DSMXX';
        document.getElementById('edit-materia').value = 'Cálculo';
        document.getElementById('edit-hora-inicio').value = '07:00';
        document.getElementById('edit-hora-fin').value = '08:00';
        document.getElementById('edit-grupo-profesor').value = 'DSMXX';
        document.getElementById('edit-dia').value = 'lunes';
        photoPreview.src = '';
        photoPreview.alt = 'Vista previa de foto';

        professorClasses = [
            {
                materia: 'Redes',
                horaInicio: '10:00',
                horaFin: '12:00',
                grupo: 'DSMYY',
                dia: 'martes'
            },
            {
                materia: 'POO',
                horaInicio: '13:00',
                horaFin: '15:00',
                grupo: 'DSMYY',
                dia: 'jueves'
            }
        ];

        renderProfessorClasses();
        resetClassEditor();
        const modal = name === 'delete' ? deleteModal : editModal;
        closeModal(modal);
    };

    editButtons.forEach(button => {
        button.addEventListener('click', () => window.openEditModal(button));
    });

    deleteButtons.forEach(button => {
        button.addEventListener('click', () => window.openDeleteModal());
    });

    document.querySelectorAll('[data-close]').forEach(button => {
        button.addEventListener('click', () => {
            const modal = button.dataset.close === 'delete' ? deleteModal : editModal;
            closeModal(modal);
        });
    });

    document.getElementById('confirm-delete').addEventListener('click', () => {
        closeModal(deleteModal);
        alert('Usuario eliminado (simulación estática).');
    });

    editForm.addEventListener('submit', event => {
        event.preventDefault();
        closeModal(editModal);
        alert('Cambios guardados (simulación estática).');
    });

    function handleClassAction(event) {
        const action = event.target.dataset.action;
        const index = Number(event.target.dataset.index);
        if (Number.isNaN(index)) return;

        if (action === 'edit-class') {
            fillClassEditor(professorClasses[index], index);
        }

        if (action === 'delete-class') {
            professorClasses.splice(index, 1);
            renderProfessorClasses();
            resetClassEditor();
        }
    }

    if (classesContainer) {
        classesContainer.addEventListener('click', handleClassAction);
    }

    if (saveClassButton) {
        saveClassButton.addEventListener('click', () => {
            const clase = {
                materia: document.getElementById('edit-materia').value,
                horaInicio: document.getElementById('edit-hora-inicio').value,
                horaFin: document.getElementById('edit-hora-fin').value,
                grupo: document.getElementById('edit-grupo-profesor').value,
                dia: document.getElementById('edit-dia').value
            };

            if (editingClassIndex >= 0) {
                professorClasses[editingClassIndex] = clase;
            } else {
                professorClasses.push(clase);
            }

            renderProfessorClasses();
            resetClassEditor();
        });
    }

    if (clearClassButton) {
        clearClassButton.addEventListener('click', resetClassEditor);
    }
});